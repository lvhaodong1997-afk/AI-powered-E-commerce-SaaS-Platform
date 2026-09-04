package cn.iocoder.yudao.module.tk.service.generation.pipeline;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationBatchDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationStepLogDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationTaskDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkMaterialLibraryDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkGenerationBatchMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.TkGenerationStepLogMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.TkGenerationTaskMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.TkMaterialLibraryMapper;
import cn.iocoder.yudao.module.tk.enums.TkGenerationStatusEnum;
import cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties;
import cn.iocoder.yudao.module.tk.framework.ffmpeg.TkFfmpegExecutableResolver;
import cn.iocoder.yudao.module.tk.service.generation.TkGenerationRouteConfigSupport;
import cn.iocoder.yudao.module.tk.service.generation.TkGenerationTaskLeaseService;
import cn.iocoder.yudao.module.tk.service.credit.TkCreditService;
import cn.iocoder.yudao.module.tk.service.generation.TkGenerationBatchProgressSupport;
import cn.iocoder.yudao.module.tk.service.log.TkBusinessLogService;
import cn.iocoder.yudao.module.tk.service.scope.TkUserScope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.module.tk.enums.TkCreditBizTypeEnum.GENERATION_TASK;

@Service
@Slf4j
public class DefaultTkGenerationPipelineService implements TkGenerationPipelineService {

    private ExecutorService executorService;
    private final Set<String> inFlightTasks = ConcurrentHashMap.newKeySet();
    private final String workerId = ManagementFactory.getRuntimeMXBean().getName();
    private final ThreadLocal<String> activeLeaseToken = new ThreadLocal<>();

    @Resource
    private TkGenerationTaskMapper taskMapper;
    @Resource
    private TkGenerationBatchMapper batchMapper;
    @Resource
    private TkGenerationStepLogMapper stepLogMapper;
    @Resource
    private TkMaterialLibraryMapper libraryMapper;
    @Resource
    private TkScriptGenerationService scriptGenerationService;
    @Resource
    private TkVoiceSynthesisService voiceSynthesisService;
    @Resource
    private TkClipPlannerService clipPlannerService;
    @Resource
    private TkVideoRenderService videoRenderService;
    @Resource
    private TkCreditService creditService;
    @Resource
    private TkBusinessLogService businessLogService;
    @Resource
    private TkGenerationProperties generationProperties;
    @Resource
    private TkGenerationTaskLeaseService taskLeaseService;
    @Resource
    private TkVideoTailQualityService videoTailQualityService;

    @PostConstruct
    public void init() {
        int workerSize = generationProperties.getQueue().getWorkerSize() == null
                ? 2 : Math.max(1, generationProperties.getQueue().getWorkerSize());
        int queueCapacity = generationProperties.getQueue().getQueueCapacity() == null
                ? 100 : Math.max(workerSize, generationProperties.getQueue().getQueueCapacity());
        executorService = new ThreadPoolExecutor(workerSize, workerSize, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity), new ThreadPoolExecutor.CallerRunsPolicy());
        submitRecoverableTasks();
    }

    @Override
    public void submit(Long tenantId, Long taskId) {
        String key = taskKey(tenantId, taskId);
        if (!inFlightTasks.add(key)) {
            return;
        }
        String leaseToken = UUID.randomUUID().toString().replace("-", "");
        int staleSeconds = generationProperties.getQueue().getStaleSeconds() == null
                ? 300 : Math.max(60, generationProperties.getQueue().getStaleSeconds());
        LocalDateTime now = LocalDateTime.now();
        boolean claimed = taskLeaseService == null || TenantUtils.execute(tenantId,
                () -> taskLeaseService.claim(taskId, leaseToken, workerId,
                        now.minusSeconds(staleSeconds), now.plusSeconds(staleSeconds)));
        if (!claimed) {
            inFlightTasks.remove(key);
            return;
        }
        try {
            executorService.submit(() -> runWithIsolatedContext(() -> {
                try {
                    activeLeaseToken.set(leaseToken);
                    TenantUtils.execute(tenantId, () -> run(taskId, leaseToken));
                } finally {
                    activeLeaseToken.remove();
                    if (taskLeaseService != null) {
                        TenantUtils.execute(tenantId, () -> taskLeaseService.release(taskId, leaseToken));
                    }
                    inFlightTasks.remove(key);
                }
            }));
        } catch (RuntimeException ex) {
            if (taskLeaseService != null) {
                TenantUtils.execute(tenantId, () -> taskLeaseService.release(taskId, leaseToken));
            }
            inFlightTasks.remove(key);
            throw ex;
        }
    }

    void runWithIsolatedContext(Runnable action) {
        SecurityContext previousSecurityContext = SecurityContextHolder.getContext();
        RequestAttributes previousRequestAttributes = RequestContextHolder.getRequestAttributes();
        try {
            SecurityContextHolder.clearContext();
            RequestContextHolder.resetRequestAttributes();
            action.run();
        } finally {
            SecurityContextHolder.clearContext();
            RequestContextHolder.resetRequestAttributes();
            SecurityContextHolder.setContext(previousSecurityContext);
            if (previousRequestAttributes != null) {
                RequestContextHolder.setRequestAttributes(previousRequestAttributes);
            }
        }
    }

    @Scheduled(fixedDelayString = "${tk.generation.queue.scan-delay-ms:10000}", initialDelayString = "${tk.generation.queue.scan-delay-ms:10000}")
    public void submitRecoverableTasks() {
        if (executorService == null) {
            return;
        }
        int batchSize = generationProperties.getQueue().getBatchSize() == null
                ? 10 : Math.max(1, generationProperties.getQueue().getBatchSize());
        int staleSeconds = generationProperties.getQueue().getStaleSeconds() == null
                ? 300 : Math.max(60, generationProperties.getQueue().getStaleSeconds());
        List<TkGenerationTaskDO> tasks = new ArrayList<>();
        tasks.addAll(taskMapper.selectPendingForQueue(batchSize));
        tasks.addAll(taskMapper.selectStaleRunningForQueue(LocalDateTime.now().minusSeconds(staleSeconds), batchSize));
        for (TkGenerationTaskDO task : tasks) {
            submit(task.getTenantId(), task.getId());
        }
    }

    private void run(Long taskId, String leaseToken) {
        String businessTraceId = null;
        try {
            TkGenerationTaskDO task = taskMapper.selectById(taskId);
            if (task == null) {
                return;
            }
            Long openingDurationMs = resolveOpeningDurationMs(task);
            if (!Objects.equals(task.getOpeningDurationMs(), openingDurationMs)) {
                task.setOpeningDurationMs(openingDurationMs);
                updateOwned(new TkGenerationTaskDO().setId(taskId).setOpeningDurationMs(openingDurationMs));
            }
            validateNativeOpeningDuration(task);
            businessTraceId = task.getBusinessTraceId();
            TkMaterialLibraryDO library = libraryMapper.selectById(task.getLibraryId());
            update(taskId, TkGenerationStatusEnum.ANALYZING, 10, "分析任务配置", null, null);
            businessLogService.info(businessTraceId, "GENERATION_TASK", taskId, "START", TkGenerationStatusEnum.ANALYZING,
                    "生成流水线开始分析", task);
            TkGeneratedScript script = resolveScript(task, library);
            finishCurrentStep(task, "SUCCESS", null, null);
            updateOwned(new TkGenerationTaskDO()
                    .setId(taskId)
                    .setTitle(script.getTitle())
                    .setReferenceDuration(script.getReferenceDuration())
                    .setTargetDuration(script.getTargetDuration())
                    .setScriptText(script.getContent())
                    .setSegmentTimeline(script.getSegmentTimeline())
                    .setStatus(TkGenerationStatusEnum.SCRIPT_READY)
                    .setProgress(30)
                    .setCurrentStep("口播文案已确认")
                    .setHeartbeatTime(LocalDateTime.now())
                    .setWorkerId(workerId));
            businessLogService.info(businessTraceId, "GENERATION_TASK", taskId, "SCRIPT_READY", TkGenerationStatusEnum.SCRIPT_READY,
                    "生成口播文案完成", script);

            task = taskMapper.selectById(taskId);
            TkAudioAsset audioAsset = resolveAudioAsset(task, script.getContent());
            finishCurrentStep(task, "SUCCESS", null, null);
            updateOwned(new TkGenerationTaskDO()
                    .setId(taskId)
                    .setAudioUrl(audioAsset.getAudioUrl())
                    .setSubtitleUrl(audioAsset.getSubtitleUrl())
                    .setStatus(TkGenerationStatusEnum.VOICE_READY)
                    .setProgress(50)
                    .setCurrentStep(Boolean.FALSE.equals(task.getVoiceEnabled()) ? "AI 配音已关闭" : "AI 配音生成完成")
                    .setHeartbeatTime(LocalDateTime.now())
                    .setWorkerId(workerId));
            businessLogService.info(businessTraceId, "GENERATION_TASK", taskId, "VOICE_READY", TkGenerationStatusEnum.VOICE_READY,
                    Boolean.FALSE.equals(task.getVoiceEnabled()) ? "配音已关闭，跳过音色生成" : "配音生成完成", audioAsset);

            update(taskId, TkGenerationStatusEnum.MATERIAL_MATCHING, 60, "正在随机抽取完整素材", null, null);
            task = taskMapper.selectById(taskId);
            Integer effectiveTargetDuration = resolveEffectiveTargetDuration(task, audioAsset);
            List<TkClipPlanItem> clipPlan = resolveClipPlan(task, script.getContent(), effectiveTargetDuration);
            finishCurrentStep(task, "SUCCESS", null, null);
            updateOwned(new TkGenerationTaskDO()
                    .setId(taskId)
                    .setClipPlan(JsonUtils.toJsonString(clipPlan))
                    .setStatus(TkGenerationStatusEnum.MATERIAL_MATCHED)
                    .setProgress(65)
                    .setCurrentStep("素材随机抽取完成")
                    .setHeartbeatTime(LocalDateTime.now())
                    .setWorkerId(workerId));
            businessLogService.info(businessTraceId, "GENERATION_TASK", taskId, "MATERIAL_MATCHED", TkGenerationStatusEnum.MATERIAL_MATCHED,
                    "素材随机抽取完成", clipPlan);

            update(taskId, TkGenerationStatusEnum.RENDERING, 66, "正在准备渲染素材", null, null);
            task = taskMapper.selectById(taskId);
            task.setTargetDuration(effectiveTargetDuration);
            TkRenderResult renderResult = videoRenderService.render(task, clipPlan,
                    (stepCode, stepName, progress, completed, total) -> updateRenderProgress(taskId, stepCode, stepName,
                            progress, completed, total));
            TailQualityRenderResult tailQualityRenderResult = rerenderLeadGenerationIfTailQualityFailed(
                    task, script.getContent(), clipPlan, effectiveTargetDuration, renderResult);
            clipPlan = tailQualityRenderResult.clipPlan;
            renderResult = tailQualityRenderResult.renderResult;
            finishCurrentStep(task, "SUCCESS", null, null);
            updateOwned(new TkGenerationTaskDO()
                    .setId(taskId)
                    .setOutputUrl(renderResult.getOutputUrl())
                    .setSubtitleUrl(renderResult.getSubtitleUrl())
                    .setSubtitleTimelineUrl(renderResult.getSubtitleTimelineUrl())
                    .setSubtitleVisualAnalysisUrl(renderResult.getSubtitleVisualAnalysisUrl())
                    .setSubtitleLayoutUrl(renderResult.getSubtitleLayoutUrl())
                    .setSubtitleAssUrl(renderResult.getSubtitleAssUrl())
                    .setStatus(TkGenerationStatusEnum.EXPORTING)
                    .setProgress(99)
                    .setCurrentStep("正在写入生成记录")
                    .setCurrentStepCode("EXPORTING")
                    .setCurrentStepCompleted(1)
                    .setCurrentStepTotal(1)
                    .setHeartbeatTime(LocalDateTime.now())
                    .setWorkerId(workerId));

            update(taskId, TkGenerationStatusEnum.SUCCESS, 100, "生成完成", null, null);
            creditService.settle(GENERATION_TASK, taskId);
            refreshBatchProgress(task.getBatchId());
            businessLogService.info(businessTraceId, "GENERATION_TASK", taskId, "SUCCESS", TkGenerationStatusEnum.SUCCESS,
                    "生成任务完成", renderResult);
        } catch (Exception ex) {
            log.error("[run][taskId({}) TK 生成流水线执行失败]", taskId, ex);
            update(taskId, TkGenerationStatusEnum.FAILED, 100, "生成失败", resolveFailCode(ex), ex.getMessage());
            creditService.refund(GENERATION_TASK, taskId, ex.getMessage());
            TkGenerationTaskDO failedTask = taskMapper.selectById(taskId);
            refreshBatchProgress(failedTask == null ? null : failedTask.getBatchId());
            businessLogService.error(businessTraceId, "GENERATION_TASK", taskId, "FAILED", TkGenerationStatusEnum.FAILED,
                    ex.getMessage(), null);
        }
    }

    private TailQualityRenderResult rerenderLeadGenerationIfTailQualityFailed(TkGenerationTaskDO task, String scriptText,
                                                                              List<TkClipPlanItem> clipPlan,
                                                                              Integer effectiveTargetDuration,
                                                                              TkRenderResult renderResult) {
        if (task == null || !TkGeminiPromptConfig.isLeadGeneration(task.getMaterialPurpose())
                || videoTailQualityService == null) {
            return new TailQualityRenderResult(clipPlan, renderResult);
        }
        TkVideoTailQualityReport report = videoTailQualityService.inspect(task, renderResult);
        businessLogService.info(task.getBusinessTraceId(), "GENERATION_TASK", task.getId(), "TAIL_QUALITY_CHECK",
                report.isRetryRecommended() ? "WARN" : "SUCCESS", report.getMessage(), report);
        if (!report.isRetryRecommended()) {
            return new TailQualityRenderResult(clipPlan, renderResult);
        }
        List<TkClipPlanItem> replannedClipPlan = clipPlannerService.replanTailForLowDynamic(
                task, clipPlan, effectiveTargetDuration);
        if (sameClipPlanMaterialSequence(clipPlan, replannedClipPlan)) {
            return new TailQualityRenderResult(clipPlan, renderResult);
        }
        updateOwned(new TkGenerationTaskDO()
                .setId(task.getId())
                .setClipPlan(JsonUtils.toJsonString(replannedClipPlan))
                .setCurrentStep("尾部画面质量重排后重新渲染")
                .setHeartbeatTime(LocalDateTime.now())
                .setWorkerId(workerId));
        businessLogService.warn(task.getBusinessTraceId(), "GENERATION_TASK", task.getId(), "TAIL_REPLAN", "RENDERING",
                "尾部画面低动态或音视频字幕不一致，已重排尾部完整素材并重新渲染", replannedClipPlan);
        TkRenderResult rerenderResult = videoRenderService.render(task, replannedClipPlan);
        TkVideoTailQualityReport retryReport = videoTailQualityService.inspect(task, rerenderResult);
        businessLogService.info(task.getBusinessTraceId(), "GENERATION_TASK", task.getId(), "TAIL_QUALITY_RECHECK",
                retryReport.isRetryRecommended() ? "WARN" : "SUCCESS", retryReport.getMessage(), retryReport);
        return new TailQualityRenderResult(replannedClipPlan, rerenderResult);
    }

    TkGeneratedScript resolveScript(TkGenerationTaskDO task, TkMaterialLibraryDO library) {
        if (task != null && StrUtil.isNotBlank(task.getScriptText())) {
            return new TkGeneratedScript(task.getTitle(), task.getScriptText(), task.getSegmentTimeline(),
                    task.getReferenceDuration(), task.getTargetDuration());
        }
        return scriptGenerationService.generateScript(task, library);
    }

    TkAudioAsset resolveAudioAsset(TkGenerationTaskDO task, String scriptText) {
        if (task == null || Boolean.FALSE.equals(task.getVoiceEnabled())) {
            return new TkAudioAsset(null, null);
        }
        if (StrUtil.isNotBlank(task.getAudioUrl())) {
            return new TkAudioAsset(task.getAudioUrl(), task.getSubtitleUrl());
        }
        String narrationScript = TkNativeOpeningSupport.resolveNarrationScript(
                scriptText, task.getSegmentTimeline(), task.getOpeningProcessMode());
        if (StrUtil.isBlank(narrationScript)) {
            return new TkAudioAsset(null, null);
        }
        return voiceSynthesisService.synthesize(task, narrationScript);
    }

    List<TkClipPlanItem> resolveClipPlan(TkGenerationTaskDO task, String scriptText, Integer effectiveTargetDuration) {
        if (task != null && StrUtil.isNotBlank(task.getClipPlan())) {
            return JsonUtils.parseArray(task.getClipPlan(), TkClipPlanItem.class);
        }
        return clipPlannerService.plan(task, scriptText, effectiveTargetDuration);
    }

    private boolean sameClipPlanMaterialSequence(List<TkClipPlanItem> left, List<TkClipPlanItem> right) {
        if (left == null || right == null || left.size() != right.size()) {
            return false;
        }
        for (int i = 0; i < left.size(); i++) {
            if (!java.util.Objects.equals(left.get(i).getMaterialVideoId(), right.get(i).getMaterialVideoId())) {
                return false;
            }
        }
        return true;
    }

    private Integer resolveEffectiveTargetDuration(TkGenerationTaskDO task, TkAudioAsset audioAsset) {
        int targetDuration = TkVideoDurationSupport.normalize(task.getTargetDuration(),
                generationProperties.getFfmpeg().getMaxTargetDuration());
        if (audioAsset == null || audioAsset.getAudioUrl() == null) {
            return resolveEffectiveTargetDuration(task, (Double) null);
        }
        Double audioDuration = probeMediaDuration(audioAsset.getAudioUrl());
        if (audioDuration == null || audioDuration <= 0D) {
            if (TkNativeOpeningSupport.isNativeMode(task.getOpeningProcessMode())) {
                throw new IllegalStateException("无法识别 AI 配音时长，为避免配音被截断，请稍后重试");
            }
            return resolveEffectiveTargetDuration(task, (Double) null);
        }
        int effectiveTargetDuration = resolveEffectiveTargetDuration(task, audioDuration);
        if (effectiveTargetDuration > targetDuration) {
            log.info("[resolveEffectiveTargetDuration][taskId({}) trace({}) targetDuration({}) audioDuration({}) effectiveTargetDuration({})]",
                    task.getId(), task.getBusinessTraceId(), targetDuration, audioDuration, effectiveTargetDuration);
        }
        return effectiveTargetDuration;
    }

    int resolveEffectiveTargetDuration(TkGenerationTaskDO task, Double bodyAudioDuration) {
        validateNativeOpeningDuration(task);
        int targetDuration = TkVideoDurationSupport.normalize(task.getTargetDuration(),
                generationProperties.getFfmpeg().getMaxTargetDuration());
        double effectiveDuration = Math.max(targetDuration,
                bodyAudioDuration == null ? 0D : bodyAudioDuration);
        if (TkNativeOpeningSupport.isNativeMode(task.getOpeningProcessMode())) {
            double openingDuration = task.getOpeningDurationMs() == null
                    ? 0D : Math.max(0D, task.getOpeningDurationMs() / 1000D);
            effectiveDuration = TkNativeOpeningSupport.resolveEffectiveDuration(
                    targetDuration, openingDuration, bodyAudioDuration == null ? 0D : bodyAudioDuration);
            int configuredMax = generationProperties.getFfmpeg().getMaxTargetDuration() == null
                    ? TkVideoDurationSupport.MAX_TARGET_DURATION
                    : generationProperties.getFfmpeg().getMaxTargetDuration();
            int renderLimit = Math.min(TkVideoDurationSupport.MAX_TARGET_DURATION, Math.max(1, configuredMax));
            if (effectiveDuration > renderLimit) {
                throw new IllegalStateException("黄金开头和正文配音总时长超过系统上限 " + renderLimit
                        + " 秒，请缩短开头视频或正文文案后重试");
            }
        }
        return TkVideoDurationSupport.normalize((int) Math.ceil(effectiveDuration),
                generationProperties.getFfmpeg().getMaxTargetDuration());
    }

    private void validateNativeOpeningDuration(TkGenerationTaskDO task) {
        if (task != null && TkNativeOpeningSupport.isNativeMode(task.getOpeningProcessMode())
                && StrUtil.isNotBlank(task.getOpeningVideoUrl())
                && (task.getOpeningDurationMs() == null || task.getOpeningDurationMs() <= 0L)) {
            throw new IllegalStateException("无法识别黄金开头视频时长，请检查视频文件或链接后重试");
        }
    }

    Long resolveOpeningDurationMs(TkGenerationTaskDO task) {
        if (task == null || !TkNativeOpeningSupport.isNativeMode(task.getOpeningProcessMode())
                || StrUtil.isBlank(task.getOpeningVideoUrl())) {
            return null;
        }
        if (task.getOpeningDurationMs() != null && task.getOpeningDurationMs() > 0L) {
            return task.getOpeningDurationMs();
        }
        Double duration = probeMediaDuration(task.getOpeningVideoUrl());
        return duration == null || duration <= 0D ? null : Math.max(1L, Math.round(duration * 1000D));
    }

    private Double probeMediaDuration(String mediaUrl) {
        try {
            Double directDuration = probeMediaDurationByFfprobe(mediaUrl);
            if (directDuration != null && directDuration > 0D) {
                return directDuration;
            }
            return probeDownloadedMediaDuration(mediaUrl);
        } catch (Exception ex) {
            log.warn("[probeMediaDuration][配音时长探测失败，按用户目标时长规划素材]", ex);
            return null;
        }
    }

    Double probeDownloadedMediaDuration(String mediaUrl) {
        if (StrUtil.isBlank(mediaUrl)) {
            return null;
        }
        File tempFile = null;
        try {
            tempFile = File.createTempFile("tk-audio-duration-", ".media");
            TkGenerationProperties.RenderDownload renderDownload = generationProperties.getRenderDownload();
            String requestUrl = DefaultTkVideoRenderService.resolveDownloadUrl(mediaUrl,
                    renderDownload.getPublicBaseUrl(), renderDownload.getInternalBaseUrl());
            int timeoutMillis = Math.max(10, renderDownload.getTimeoutSeconds() == null
                    ? 180 : renderDownload.getTimeoutSeconds()) * 1000;
            try (HttpResponse response = HttpRequest.get(requestUrl).timeout(timeoutMillis).execute()) {
                if (!response.isOk()) {
                    return null;
                }
                FileUtil.writeBytes(response.bodyBytes(), tempFile);
            }
            return probeMediaDurationByFfprobe(tempFile.getAbsolutePath());
        } catch (Exception ex) {
            log.warn("[probeDownloadedMediaDuration][audio duration fallback failed, url({})]", mediaUrl, ex);
            return null;
        } finally {
            FileUtil.del(tempFile);
        }
    }

    private Double probeMediaDurationByFfprobe(String mediaUrl) throws Exception {
        Process process = new ProcessBuilder(Arrays.asList(ffprobe(), "-v", "error",
                "-show_entries", "format=duration", "-of", "default=noprint_wrappers=1:nokey=1", mediaUrl))
                .redirectErrorStream(true)
                .start();
        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            return null;
        }
        if (process.exitValue() != 0) {
            return null;
        }
        String output = readProcessOutput(process);
        if (StrUtil.isBlank(output)) {
            return null;
        }
        try {
            return Double.parseDouble(output.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String readProcessOutput(Process process) throws Exception {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        }
        return builder.toString();
    }

    private String ffprobe() {
        return TkFfmpegExecutableResolver.ffprobe(generationProperties.getFfmpeg().getFfprobePath());
    }

    private void update(Long taskId, String status, Integer progress, String currentStep, String failCode, String failReason) {
        renewActiveLease(taskId);
        LocalDateTime now = LocalDateTime.now();
        TkGenerationTaskDO existing = taskMapper.selectById(taskId);
        finishCurrentStep(existing, TkGenerationStatusEnum.FAILED.equals(status) ? "FAILED" : "SUCCESS", failCode, failReason);
        com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<TkGenerationTaskDO> wrapper = Wrappers.<TkGenerationTaskDO>update()
                .eq("id", taskId)
                .set("status", status)
                .set("progress", progress)
                .set("current_step", currentStep)
                .set("current_step_code", status)
                .set("current_step_completed", null)
                .set("current_step_total", null)
                .set("fail_code", failCode)
                .set("fail_reason", failReason)
                .set("worker_id", workerId)
                .set("heartbeat_time", now)
                .set("step_started_at", now)
                .set("step_finished_at",
                        TkGenerationStatusEnum.SUCCESS.equals(status) || TkGenerationStatusEnum.FAILED.equals(status)
                                ? now : null);
        if (StrUtil.isNotBlank(activeLeaseToken.get())) {
            wrapper.eq("lease_token", activeLeaseToken.get());
        }
        taskMapper.update(null, wrapper);
    }

    private void updateRenderProgress(Long taskId, String stepCode, String stepName, int progress,
                                      int completed, int total) {
        renewActiveLease(taskId);
        com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<TkGenerationTaskDO> wrapper = Wrappers.<TkGenerationTaskDO>update()
                .eq("id", taskId)
                .set("status", TkGenerationStatusEnum.RENDERING)
                .set("progress", Math.max(66, Math.min(progress, 99)))
                .set("current_step", stepName)
                .set("current_step_code", stepCode)
                .set("current_step_completed", Math.max(0, completed))
                .set("current_step_total", Math.max(0, total))
                .set("worker_id", workerId)
                .set("heartbeat_time", LocalDateTime.now());
        if (StrUtil.isNotBlank(activeLeaseToken.get())) {
            wrapper.eq("lease_token", activeLeaseToken.get());
        }
        taskMapper.update(null, wrapper);
    }

    private void updateOwned(TkGenerationTaskDO update) {
        if (update == null || update.getId() == null) {
            return;
        }
        if (StrUtil.isBlank(activeLeaseToken.get())) {
            taskMapper.updateById(update);
            return;
        }
        renewActiveLease(update.getId());
        taskMapper.update(update, Wrappers.<TkGenerationTaskDO>update()
                .eq("id", update.getId())
                .eq("lease_token", activeLeaseToken.get()));
    }

    private void renewActiveLease(Long taskId) {
        if (taskLeaseService == null || StrUtil.isBlank(activeLeaseToken.get())) {
            return;
        }
        int staleSeconds = generationProperties.getQueue().getStaleSeconds() == null
                ? 300 : Math.max(60, generationProperties.getQueue().getStaleSeconds());
        if (!taskLeaseService.renew(taskId, activeLeaseToken.get(), LocalDateTime.now().plusSeconds(staleSeconds))) {
            throw new IllegalStateException("generation task lease lost");
        }
    }

    private void finishCurrentStep(TkGenerationTaskDO task, String status, String failCode, String failReason) {
        if (task == null || task.getId() == null || task.getCurrentStep() == null) {
            return;
        }
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = task.getStepStartedAt() == null
                ? (task.getCreateTime() == null ? endTime : task.getCreateTime())
                : task.getStepStartedAt();
        long durationMillis = Math.max(0L, Duration.between(startTime, endTime).toMillis());
        TkGenerationStepLogDO log = TkGenerationStepLogDO.builder()
                .taskId(task.getId())
                .batchId(task.getBatchId())
                .stepCode(task.getStatus())
                .stepName(task.getCurrentStep())
                .status(status)
                .startTime(startTime)
                .endTime(endTime)
                .durationMillis(durationMillis)
                .failCode(failCode)
                .failReason(failReason)
                .retryCount(task.getRetryCount() == null ? 0 : task.getRetryCount())
                .workerId(workerId)
                .build();
        log.setTenantId(task.getTenantId());
        stepLogMapper.insert(log);
    }

    private void refreshBatchProgress(Long batchId) {
        if (batchId == null) {
            return;
        }
        TkGenerationBatchDO batch = batchMapper.selectById(batchId);
        if (batch == null) {
            return;
        }
        TkUserScope batchScope = new TkUserScope(null, batch.getTenantId(), "PLATFORM_ADMIN", null);
        List<TkGenerationTaskDO> tasks = taskMapper.selectListByBatchId(batchId, batchScope);
        TkGenerationBatchProgressSupport.BatchProgress progress =
                TkGenerationBatchProgressSupport.summarize(batch.getExpectedVideoCount(), tasks);
        String failSummary = tasks.stream()
                .filter(task -> TkGenerationStatusEnum.FAILED.equals(task.getStatus()))
                .map(task -> task.getFailCode() == null ? task.getFailReason() : task.getFailCode())
                .filter(item -> item != null && !item.isEmpty())
                .limit(3)
                .collect(Collectors.joining("; "));
        batchMapper.updateById(new TkGenerationBatchDO()
                .setId(batchId)
                .setCreatedTaskCount(progress.getCreatedCount())
                .setSuccessTaskCount(progress.getSuccessCount())
                .setFailedTaskCount(progress.getFailedCount())
                .setRunningTaskCount(progress.getRunningCount())
                .setProgressPercent(progress.getProgressPercent())
                .setStatus(progress.getStatus())
                .setFailSummary(failSummary));
    }

    private String resolveFailCode(Exception ex) {
        String message = ex.getMessage() == null ? "" : ex.getMessage();
        if (message.contains("随机混剪") || message.contains("可用于随机混剪")) {
            return "MATERIAL_EMPTY";
        }
        if (message.contains("目标时长过短") || message.contains("所有可用素材都过长")) {
            return "MATERIAL_TOO_LONG_FOR_TARGET";
        }
        if (message.contains("素材库没有可用于混剪")) {
            return "MATERIAL_EMPTY";
        }
        if (message.contains("素材库可用视频总时长不足")) {
            return "MATERIAL_DURATION_NOT_ENOUGH";
        }
        if (message.contains("下载文件失败") || message.contains("文件 URL 不是可下载")) {
            return "MATERIAL_DOWNLOAD_FAILED";
        }
        if (message.contains("配音") || message.contains("TTS") || message.contains("DashScope")) {
            return "TTS_FAILED";
        }
        if (message.contains("字幕") || message.contains("ASR") || message.contains("subtitle")
                || message.contains("Subtitle")) {
            return "SUBTITLE_FAILED";
        }
        if (message.contains("FFmpeg") || message.contains("ffmpeg") || message.contains("合成失败")) {
            return "FFMPEG_RENDER_FAILED";
        }
        if (message.contains("上传") || message.contains("createFile")) {
            return "UPLOAD_FAILED";
        }
        return "PIPELINE_INTERRUPTED";
    }

    private String taskKey(Long tenantId, Long taskId) {
        return tenantId + ":" + taskId;
    }

    private static class TailQualityRenderResult {

        private final List<TkClipPlanItem> clipPlan;
        private final TkRenderResult renderResult;

        private TailQualityRenderResult(List<TkClipPlanItem> clipPlan, TkRenderResult renderResult) {
            this.clipPlan = clipPlan;
            this.renderResult = renderResult;
        }

    }

    @PreDestroy
    public void destroy() {
        if (executorService != null) {
            executorService.shutdown();
        }
    }

}

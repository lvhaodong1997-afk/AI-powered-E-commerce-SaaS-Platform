package cn.iocoder.yudao.module.tk.controller.admin.generation;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.tk.controller.admin.generation.vo.TkGenerationPrecheckRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.generation.vo.TkGenerationTaskCreateReqVO;
import cn.iocoder.yudao.module.tk.controller.admin.generation.vo.TkGenerationTaskPageReqVO;
import cn.iocoder.yudao.module.tk.controller.admin.generation.vo.TkGenerationTaskRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.generation.vo.TkGenerationTaskStatusRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.generation.vo.TkGenerationTaskSummaryRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.generation.vo.TkVoicePreviewReqVO;
import cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo.TkTiktokPublishUrlRespVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationTaskDO;
import cn.iocoder.yudao.module.tk.service.generation.TkGenerationPrecheckService;
import cn.iocoder.yudao.module.tk.service.generation.TkGenerationTaskService;
import cn.iocoder.yudao.module.tk.service.generation.pipeline.TkDashScopeTtsClient;
import cn.iocoder.yudao.module.tk.service.generation.pipeline.TkTtsProviderEnum;
import cn.iocoder.yudao.module.tk.service.generation.pipeline.TkVoiceProviderRouter;
import cn.iocoder.yudao.module.tk.service.generation.pipeline.TkVoiceSynthesisRequest;
import cn.iocoder.yudao.module.tk.service.generation.pipeline.TkVoiceTtsClient;
import cn.iocoder.yudao.module.tk.service.tiktok.TkTiktokPublishService;
import cn.iocoder.yudao.module.tk.service.upload.TkGenerationOutputStorageService;
import cn.iocoder.yudao.module.tk.service.voice.TkMimoVoiceSelection;
import cn.iocoder.yudao.module.tk.service.voice.TkVoiceProfileService;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - TK 智能生成")
@RestController
@RequestMapping("/tk/generation")
@Validated
public class TkGenerationTaskController {

    private static final String VOICE_PREVIEW_TEXT = "这是当前 AI 配音音色试听，适合 TikTok 电商短视频口播。";
    private static final Object VOICE_PREVIEW_CACHE_LOCK = new Object();

    @Resource
    private TkGenerationTaskService generationTaskService;
    @Resource
    private TkGenerationPrecheckService precheckService;
    @Resource
    private TkDashScopeTtsClient dashScopeTtsClient;
    @Resource
    private TkVoiceProviderRouter voiceProviderRouter;
    @Resource
    private TkVoiceProfileService voiceProfileService;
    @Resource
    private TkTiktokPublishService tiktokPublishService;
    @Resource
    private AdminUserApi adminUserApi;
    @Resource
    private TkGenerationOutputStorageService generationOutputStorageService;

    @PostMapping("/precheck")
    @Operation(summary = "生成任务预检")
    @PreAuthorize("@ss.hasPermission('tk:generation:create')")
    public CommonResult<TkGenerationPrecheckRespVO> precheckGenerationTask(@Valid @RequestBody TkGenerationTaskCreateReqVO createReqVO) {
        return success(precheckService.precheck(createReqVO));
    }

    @PostMapping("/create")
    @Operation(summary = "创建智能生成任务")
    @PreAuthorize("@ss.hasPermission('tk:generation:create')")
    public CommonResult<Long> createGenerationTask(@Valid @RequestBody TkGenerationTaskCreateReqVO createReqVO) {
        return success(generationTaskService.createGenerationTask(createReqVO));
    }

    @PostMapping("/create-batch")
    @Operation(summary = "Batch create generation tasks")
    @PreAuthorize("@ss.hasPermission('tk:generation:create')")
    public CommonResult<List<Long>> createGenerationTasks(@Valid @RequestBody TkGenerationTaskCreateReqVO createReqVO) {
        return success(generationTaskService.createGenerationTasks(createReqVO));
    }

    @PostMapping("/create-with-opening")
    @Operation(summary = "创建智能生成任务（含黄金三秒开头视频）")
    @PreAuthorize("@ss.hasPermission('tk:generation:create')")
    public CommonResult<Long> createGenerationTaskWithOpening(@Valid TkGenerationTaskCreateReqVO createReqVO,
                                                              @RequestParam(value = "openingVideoFile", required = false) MultipartFile openingVideoFile) {
        return success(generationTaskService.createGenerationTask(createReqVO, openingVideoFile));
    }

    @PostMapping("/retry")
    @Operation(summary = "重试失败的智能生成任务")
    @PreAuthorize("@ss.hasPermission('tk:generation:create')")
    public CommonResult<Boolean> retryGenerationTask(@RequestParam("id") Long id) {
        generationTaskService.retryGenerationTask(id);
        return success(true);
    }

    @GetMapping("/page")
    @Operation(summary = "获得智能生成任务分页")
    @PreAuthorize("@ss.hasPermission('tk:generation:query')")
    public CommonResult<PageResult<TkGenerationTaskRespVO>> getGenerationTaskPage(@Valid TkGenerationTaskPageReqVO pageReqVO) {
        PageResult<TkGenerationTaskDO> pageResult = generationTaskService.getGenerationTaskPage(pageReqVO);
        PageResult<TkGenerationTaskRespVO> respResult = BeanUtils.toBean(pageResult, TkGenerationTaskRespVO.class);
        enrichTaskDisplayFields(pageResult.getList(), respResult.getList());
        refreshOutputUrls(pageResult.getList(), respResult.getList());
        enrichLatestPublishUrls(respResult.getList());
        return success(respResult);
    }

    @GetMapping("/page-summary")
    @Operation(summary = "获得智能生成任务摘要分页")
    @PreAuthorize("@ss.hasPermission('tk:generation:query')")
    public CommonResult<PageResult<TkGenerationTaskSummaryRespVO>> getGenerationTaskSummaryPage(@Valid TkGenerationTaskPageReqVO pageReqVO) {
        PageResult<TkGenerationTaskDO> pageResult = generationTaskService.getGenerationTaskSummaryPage(pageReqVO);
        PageResult<TkGenerationTaskSummaryRespVO> respResult = BeanUtils.toBean(pageResult, TkGenerationTaskSummaryRespVO.class);
        enrichTaskSummaryDisplayFields(pageResult.getList(), respResult.getList());
        refreshSummaryOutputUrls(pageResult.getList(), respResult.getList());
        enrichLatestPublishUrlSummaries(respResult.getList());
        return success(respResult);
    }

    @GetMapping("/status-batch")
    @Operation(summary = "批量获得智能生成任务状态")
    @PreAuthorize("@ss.hasPermission('tk:generation:query')")
    public CommonResult<List<TkGenerationTaskStatusRespVO>> getGenerationTaskStatusBatch(@RequestParam("ids") String ids) {
        List<Long> parsedIds = Arrays.stream(ids.split(","))
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .filter(item -> item.matches("\\d+"))
                .map(Long::valueOf)
                .distinct()
                .limit(50)
                .collect(Collectors.toList());
        List<TkGenerationTaskDO> tasks = generationTaskService.getGenerationTaskStatusBatch(parsedIds);
        List<TkGenerationTaskStatusRespVO> result = BeanUtils.toBean(tasks, TkGenerationTaskStatusRespVO.class);
        refreshStatusOutputUrls(tasks, result);
        return success(result);
    }

    @GetMapping("/get")
    @Operation(summary = "获得智能生成任务")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('tk:generation:query')")
    public CommonResult<TkGenerationTaskRespVO> getGenerationTask(@RequestParam("id") Long id) {
        TkGenerationTaskDO task = generationTaskService.getGenerationTask(id);
        TkGenerationTaskRespVO respVO = BeanUtils.toBean(task, TkGenerationTaskRespVO.class);
        enrichTaskDisplayFields(Arrays.asList(task), Arrays.asList(respVO));
        refreshOutputUrls(Arrays.asList(task), Arrays.asList(respVO));
        enrichLatestPublishUrls(Arrays.asList(respVO));
        return success(respVO);
    }

    private void refreshOutputUrls(List<TkGenerationTaskDO> tasks, List<TkGenerationTaskRespVO> items) {
        Map<Long, TkGenerationTaskDO> taskMap = tasks.stream()
                .filter(task -> task.getId() != null)
                .collect(Collectors.toMap(TkGenerationTaskDO::getId, task -> task, (left, right) -> left));
        items.forEach(item -> item.setOutputUrl(generationOutputStorageService.refreshGeneratedAssetReadUrl(
                taskMap.get(item.getId()), item.getOutputUrl(), buildDownloadFileName(
                        taskMap.get(item.getId()), item.getCreatorName(), item.getDailyUserVideoNo()))));
    }

    private void refreshSummaryOutputUrls(List<TkGenerationTaskDO> tasks, List<TkGenerationTaskSummaryRespVO> items) {
        Map<Long, TkGenerationTaskDO> taskMap = tasks.stream()
                .filter(task -> task.getId() != null)
                .collect(Collectors.toMap(TkGenerationTaskDO::getId, task -> task, (left, right) -> left));
        items.forEach(item -> item.setOutputUrl(generationOutputStorageService.refreshGeneratedAssetReadUrl(
                taskMap.get(item.getId()), item.getOutputUrl(), buildDownloadFileName(
                        taskMap.get(item.getId()), item.getCreatorName(), item.getDailyUserVideoNo()))));
    }

    private void refreshStatusOutputUrls(List<TkGenerationTaskDO> tasks, List<TkGenerationTaskStatusRespVO> items) {
        Map<Long, TkGenerationTaskDO> taskMap = tasks.stream()
                .filter(task -> task.getId() != null)
                .collect(Collectors.toMap(TkGenerationTaskDO::getId, task -> task, (left, right) -> left));
        items.forEach(item -> item.setOutputUrl(generationOutputStorageService.refreshGeneratedAssetReadUrl(
                taskMap.get(item.getId()), item.getOutputUrl(), "")));
    }

    private String buildDownloadFileName(TkGenerationTaskDO task, String creatorName, Integer dailyNo) {
        if (task == null || task.getCreateTime() == null || StrUtil.isBlank(creatorName) || dailyNo == null) {
            return "";
        }
        return task.getCreateTime().toLocalDate().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE)
                + "-" + creatorName + "-" + StrUtil.padPre(String.valueOf(dailyNo), 3, '0') + ".mp4";
    }

    private void enrichTaskDisplayFields(List<TkGenerationTaskDO> tasks, List<TkGenerationTaskRespVO> items) {
        Map<Long, Integer> dailyNoMap = generationTaskService.getDailyUserVideoNoMap(tasks);
        Map<String, String> creatorNameMap = resolveCreatorNameMap(items.stream()
                .map(TkGenerationTaskRespVO::getCreator)
                .collect(Collectors.toSet()));
        items.forEach(item -> {
            item.setDailyUserVideoNo(dailyNoMap.get(item.getId()));
            item.setCreatorName(creatorNameMap.get(item.getCreator()));
        });
    }

    private void enrichTaskSummaryDisplayFields(List<TkGenerationTaskDO> tasks, List<TkGenerationTaskSummaryRespVO> items) {
        Map<Long, Integer> dailyNoMap = generationTaskService.getDailyUserVideoNoMap(tasks);
        Map<String, String> creatorNameMap = resolveCreatorNameMap(items.stream()
                .map(TkGenerationTaskSummaryRespVO::getCreator)
                .collect(Collectors.toSet()));
        items.forEach(item -> {
            item.setDailyUserVideoNo(dailyNoMap.get(item.getId()));
            item.setCreatorName(creatorNameMap.get(item.getCreator()));
        });
    }

    private Map<String, String> resolveCreatorNameMap(Collection<String> creators) {
        Map<String, String> result = new HashMap<>();
        if (creators == null || creators.isEmpty()) {
            return result;
        }
        Set<Long> userIds = creators.stream()
                .filter(StrUtil::isNotBlank)
                .filter(item -> item.matches("\\d+"))
                .map(Long::valueOf)
                .collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return result;
        }
        Map<Long, AdminUserRespDTO> userMap = adminUserApi.getUserMap(userIds);
        userIds.forEach(userId -> {
            AdminUserRespDTO user = userMap.get(userId);
            result.put(String.valueOf(userId), user == null
                    ? "用户ID " + userId
                    : StrUtil.blankToDefault(user.getNickname(), "用户ID " + userId));
        });
        return result;
    }

    private void enrichLatestPublishUrls(List<TkGenerationTaskRespVO> items) {
        List<Long> ids = items.stream()
                .map(TkGenerationTaskRespVO::getId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toList());
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, TkTiktokPublishUrlRespVO> latestMap = tiktokPublishService.getLatestPublishUrlMap(ids);
        items.forEach(item -> {
            TkTiktokPublishUrlRespVO publishUrl = latestMap.get(item.getId());
            if (publishUrl != null) {
                item.setLatestPublishDetailId(publishUrl.getPublishDetailId());
                item.setLatestPublishAccountName(publishUrl.getAccountDisplayName());
                item.setLatestPublishUrl(publishUrl.getPublishUrl());
                item.setLatestPublishUrlRegisteredTime(publishUrl.getPublishUrlRegisteredTime());
            }
        });
    }

    private void enrichLatestPublishUrlSummaries(List<TkGenerationTaskSummaryRespVO> items) {
        List<Long> ids = items.stream()
                .map(TkGenerationTaskSummaryRespVO::getId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toList());
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, TkTiktokPublishUrlRespVO> latestMap = tiktokPublishService.getLatestPublishUrlMap(ids);
        items.forEach(item -> {
            TkTiktokPublishUrlRespVO publishUrl = latestMap.get(item.getId());
            if (publishUrl != null) {
                item.setLatestPublishDetailId(publishUrl.getPublishDetailId());
                item.setLatestPublishAccountName(publishUrl.getAccountDisplayName());
                item.setLatestPublishUrl(publishUrl.getPublishUrl());
                item.setLatestPublishUrlRegisteredTime(publishUrl.getPublishUrlRegisteredTime());
            }
        });
    }

    @PostMapping(value = "/voice-preview", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @Operation(summary = "试听 AI 配音音色")
    @PreAuthorize("@ss.hasPermission('tk:generation:create')")
    public ResponseEntity<byte[]> previewVoice(@Valid @RequestBody TkVoicePreviewReqVO previewReqVO) {
        String provider = TkTtsProviderEnum.normalize(previewReqVO.getTtsProvider());
        if (TkTtsProviderEnum.DASHSCOPE.equals(provider)) {
            previewReqVO.setVoiceCode(voiceProfileService.resolveVoiceSelection(
                    previewReqVO.getVoiceProfileId(), previewReqVO.getVoiceCode()));
        } else if (TkTtsProviderEnum.MIMO.equals(provider)) {
            TkMimoVoiceSelection selection = voiceProfileService.resolveMimoVoiceSelection(
                    previewReqVO.getVoiceProfileId(), previewReqVO.getMimoVoiceMode(), previewReqVO.getMimoVoiceCode(),
                    previewReqVO.getMimoVoicePrompt(), previewReqVO.getMimoVoiceSampleUrl());
            previewReqVO.setMimoVoiceMode(selection.getMode());
            previewReqVO.setMimoVoiceCode(selection.getCode());
            previewReqVO.setMimoVoicePrompt(selection.getPrompt());
            previewReqVO.setMimoVoiceSampleUrl(selection.getSampleUrl());
        }
        TkVoiceSynthesisRequest request = TkVoiceSynthesisRequest.builder()
                .text(VOICE_PREVIEW_TEXT)
                .voiceCode(previewReqVO.getVoiceCode())
                .targetLanguage(previewReqVO.getTargetLanguage())
                .mimoVoiceMode(previewReqVO.getMimoVoiceMode())
                .mimoVoiceCode(previewReqVO.getMimoVoiceCode())
                .mimoVoicePrompt(previewReqVO.getMimoVoicePrompt())
                .mimoVoiceSampleUrl(previewReqVO.getMimoVoiceSampleUrl())
                .build();
        TkVoiceTtsClient client = voiceProviderRouter.resolve(provider);
        String format = StrUtil.blankToDefault(client.audioFormat(), "mp3");
        byte[] audioBytes = TkTtsProviderEnum.DASHSCOPE.equals(provider)
                ? getCachedVoicePreview(previewReqVO, format)
                : client.synthesize(request);
        String contentType = StrUtil.equalsIgnoreCase(format, "mp3")
                ? "audio/mpeg" : "audio/" + format;
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .body(audioBytes);
    }

    private byte[] getCachedVoicePreview(TkVoicePreviewReqVO previewReqVO, String format) {
        File cacheFile = buildVoicePreviewCacheFile(previewReqVO, format);
        try {
            if (cacheFile.isFile() && cacheFile.length() > 0) {
                return Files.readAllBytes(cacheFile.toPath());
            }
            synchronized (VOICE_PREVIEW_CACHE_LOCK) {
                if (!cacheFile.isFile() || cacheFile.length() <= 0) {
                    byte[] audioBytes = dashScopeTtsClient.synthesize(
                            VOICE_PREVIEW_TEXT, previewReqVO.getVoiceCode(), previewReqVO.getTargetLanguage());
                    Files.createDirectories(cacheFile.getParentFile().toPath());
                    Files.write(cacheFile.toPath(), audioBytes);
                }
                return Files.readAllBytes(cacheFile.toPath());
            }
        } catch (Exception ex) {
            throw new IllegalStateException("AI 配音试听音频缓存处理失败：" + ex.getMessage(), ex);
        }
    }

    private File buildVoicePreviewCacheFile(TkVoicePreviewReqVO previewReqVO, String format) {
        String cacheKey = StrUtil.join("|",
                VOICE_PREVIEW_TEXT,
                StrUtil.blankToDefault(previewReqVO.getVoiceCode(), ""),
                previewReqVO.getVoiceProfileId() == null ? "" : previewReqVO.getVoiceProfileId().toString(),
                StrUtil.blankToDefault(previewReqVO.getTargetLanguage(), ""),
                StrUtil.blankToDefault(format, "mp3"));
        return new File(resolveVoicePreviewCacheDir(), sha256(cacheKey) + "." + normalizeAudioExtension(format));
    }

    private File resolveVoicePreviewCacheDir() {
        String configuredDir = System.getProperty("tk.voice-preview.cache-dir");
        if (StrUtil.isNotBlank(configuredDir)) {
            return new File(configuredDir);
        }
        File dataTkDir = new File("/data/Tk");
        if (dataTkDir.isDirectory()) {
            return new File(dataTkDir, "voice-preview-cache");
        }
        String userDir = System.getProperty("user.dir");
        if (StrUtil.isNotBlank(userDir)) {
            return new File(userDir, "voice-preview-cache");
        }
        return new File(System.getProperty("java.io.tmpdir"), "tk-voice-preview-cache");
    }

    private String normalizeAudioExtension(String format) {
        String normalized = StrUtil.blankToDefault(format, "mp3").toLowerCase();
        return normalized.replaceAll("[^a-z0-9]", "");
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte item : hash) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("生成试听音频缓存 Key 失败", ex);
        }
    }

}

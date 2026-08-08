package cn.iocoder.yudao.module.tk.service.reference;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.tk.controller.admin.reference.vo.TkReferenceAnalyzeReqVO;
import cn.iocoder.yudao.module.tk.controller.admin.reference.vo.TkReferenceAnalysisPageReqVO;
import cn.iocoder.yudao.module.tk.controller.admin.reference.vo.TkReferenceAnalysisRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.reference.vo.TkReferenceScriptOptionRespVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkMaterialLibraryDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkReferenceAnalysisDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkReferenceScriptOptionDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkMaterialLibraryMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.TkReferenceAnalysisMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.TkReferenceScriptOptionMapper;
import cn.iocoder.yudao.module.tk.service.generation.pipeline.TkAiImageInput;
import cn.iocoder.yudao.module.tk.service.generation.pipeline.TkGeminiClient;
import cn.iocoder.yudao.module.tk.service.generation.pipeline.TkGeminiPromptConfig;
import cn.iocoder.yudao.module.tk.service.generation.pipeline.TkLanguageSupport;
import cn.iocoder.yudao.module.tk.service.generation.pipeline.TkVideoDurationSupport;
import cn.iocoder.yudao.module.tk.service.credit.TkCreditService;
import cn.iocoder.yudao.module.tk.service.log.TkBusinessLogService;
import cn.iocoder.yudao.module.tk.service.log.TkBusinessTraceIdGenerator;
import cn.iocoder.yudao.module.tk.service.material.TkMaterialLibraryService;
import cn.iocoder.yudao.module.tk.service.scope.TkDataScopeService;
import cn.iocoder.yudao.module.tk.service.scope.TkUserScope;
import cn.iocoder.yudao.module.tk.service.config.TkApiKeyConfigService;
import cn.iocoder.yudao.module.tk.service.reference.ai.TkReferenceAiAnalysisContext;
import cn.iocoder.yudao.module.tk.service.reference.ai.TkReferenceAiAnalysisResult;
import cn.iocoder.yudao.module.tk.service.reference.ai.TkReferenceAiAnalysisRouter;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.lang.management.ManagementFactory;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.tk.enums.ErrorCodeConstants.*;
import static cn.iocoder.yudao.module.tk.enums.TkCreditBizTypeEnum.REFERENCE_ANALYSIS;

@Service
@Validated
@Slf4j
public class TkReferenceAnalysisServiceImpl implements TkReferenceAnalysisService {

    private static final int SCRIPT_OPTION_COUNT = 12;
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_WAITING = "WAITING";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final int DEFAULT_WORKER_SIZE = 2;
    private static final int RECOVERY_BATCH_SIZE = 10;
    private static final int RECOVERY_STALE_SECONDS = 120;
    private static final Pattern SOURCE_URL_PATTERN = Pattern.compile("https?://[^\\s\\u4e00-\\u9fa5]+");
    private static final Pattern DURATION_SECONDS_PATTERN = Pattern.compile("\\d+");
    private static final Pattern STRING_LIST_SPLIT_PATTERN = Pattern.compile("\\s*(?:,|，|、|;|；|\\||｜|->|→|\\r?\\n)\\s*");
    private static final Pattern NUMBER_TEXT_PATTERN = Pattern.compile("-?\\d+(?:\\.\\d+)?");

    private ExecutorService executorService;
    private final Set<String> inFlightAnalyses = ConcurrentHashMap.newKeySet();
    private final String workerId = ManagementFactory.getRuntimeMXBean().getName();

    @Resource
    private TkReferenceAnalysisMapper analysisMapper;
    @Resource
    private TkReferenceScriptOptionMapper scriptOptionMapper;
    @Resource
    private TkMaterialLibraryMapper materialLibraryMapper;
    @Resource
    private TkMaterialLibraryService materialLibraryService;
    @Resource
    private TkDataScopeService dataScopeService;
    @Resource
    private TkGeminiClient geminiClient;
    @Resource
    private AdminUserApi adminUserApi;
    @Resource
    private TkApiKeyConfigService apiKeyConfigService;
    @Resource
    private TkReferenceVideoContentService referenceVideoContentService;
    @Resource
    private TkReferenceAiAnalysisRouter referenceAiAnalysisRouter;
    @Resource
    private TkCreditService creditService;
    @Resource
    private TkBusinessLogService businessLogService;

    @PostConstruct
    public void init() {
        executorService = Executors.newFixedThreadPool(DEFAULT_WORKER_SIZE);
    }

    @Override
    public TkReferenceAnalysisRespVO analyze(TkReferenceAnalyzeReqVO reqVO) {
        TkMaterialLibraryDO library = materialLibraryService.validateMaterialLibraryReadable(reqVO.getLibraryId());
        reqVO.setTargetLanguage(TkLanguageSupport.normalize(reqVO.getTargetLanguage()));
        reqVO.setReferenceDuration(TkVideoDurationSupport.normalize(reqVO.getReferenceDuration()));
        reqVO.setMaterialPurpose(TkGeminiPromptConfig.normalizeMaterialPurpose(reqVO.getMaterialPurpose()));
        reqVO.setAnalysisProvider(TkReferenceAnalysisProvider.normalize(reqVO.getAnalysisProvider()));
        TkUserScope scope = dataScopeService.getCurrentScope();
        Long companyId = resolveCompanyId(reqVO.getCompanyId(), library, scope);

        TkReferenceAnalysisRespVO[] result = new TkReferenceAnalysisRespVO[1];
        TenantUtils.execute(library.getTenantId(), () -> result[0] = analyzeWithinTenant(reqVO, library, companyId, scope));
        return result[0];
    }

    @Override
    public TkReferenceAnalysisRespVO regenerateScriptOptions(Long id, Integer referenceDuration) {
        TkReferenceAnalysisDO analysis = validateAnalysisReadable(id);
        dataScopeService.validateWritable(analysis.getTenantId(), analysis.getCompanyId());
        TkMaterialLibraryDO library = materialLibraryService.validateMaterialLibraryReadable(analysis.getLibraryId());
        if (!analysis.getTenantId().equals(library.getTenantId())) {
            throw exception(TK_REFERENCE_BINDING_MISMATCH);
        }

        TkReferenceAnalysisRespVO[] result = new TkReferenceAnalysisRespVO[1];
        int targetDuration = TkVideoDurationSupport.normalize(referenceDuration);
        TenantUtils.execute(library.getTenantId(), () -> result[0] = regenerateScriptOptionsWithinTenant(analysis, library, targetDuration));
        return result[0];
    }

    private TkReferenceAnalysisRespVO analyzeWithinTenant(TkReferenceAnalyzeReqVO reqVO, TkMaterialLibraryDO library,
                                                           Long companyId, TkUserScope scope) {
        Long tenantId = library.getTenantId();
        String businessTraceId = TkBusinessTraceIdGenerator.generate(tenantId);
        Long creditLogId = null;
        try {
            businessLogService.info(businessTraceId, "REFERENCE_ANALYSIS", null, "START", STATUS_WAITING,
                    StrUtil.format("开始对标分析：{}", reqVO.getSourceUrl()), reqVO);
            if (!Boolean.TRUE.equals(reqVO.getForceRefresh())) {
                TkReferenceAnalysisDO latest = analysisMapper.selectLatestReusable(reqVO.getLibraryId(), reqVO.getSourceUrl(),
                        reqVO.getTargetLanguage(), reqVO.getReferenceDuration(), reqVO.getMaterialPurpose(),
                        reqVO.getAnalysisProvider(), scope);
                if (latest != null) {
                    if (STATUS_SUCCESS.equals(latest.getStatus()) && isLegacyFallbackAnalysis(latest)) {
                        log.warn("[analyzeWithinTenant][analysisId({}) sourceUrl({}) 命中历史兜底结果，跳过缓存并重新分析]",
                                latest.getId(), reqVO.getSourceUrl());
                    } else {
                        String latestTraceId = StrUtil.blankToDefault(latest.getBusinessTraceId(), businessTraceId);
                        String action = STATUS_SUCCESS.equals(latest.getStatus()) ? "CACHE_HIT" : "TASK_REUSE";
                        businessLogService.info(latestTraceId, "REFERENCE_ANALYSIS", latest.getId(), action, latest.getStatus(),
                                STATUS_SUCCESS.equals(latest.getStatus()) ? "命中历史对标分析结果" : "命中正在执行的对标分析任务", latest);
                        return buildResp(latest);
                    }
                }
            }

            creditLogId = creditService.freezeForReferenceAnalysis(tenantId);
            TkReferenceAnalysisDO analysis = TkReferenceAnalysisDO.builder()
                    .businessTraceId(businessTraceId)
                    .companyId(companyId)
                    .libraryId(reqVO.getLibraryId())
                    .sourceUrl(reqVO.getSourceUrl())
                    .targetLanguage(reqVO.getTargetLanguage())
                    .referenceDuration(reqVO.getReferenceDuration())
                    .materialPurpose(reqVO.getMaterialPurpose())
                    .analysisProvider(reqVO.getAnalysisProvider())
                    .sourceDomain(extractDomain(reqVO.getSourceUrl()))
                    .productName(library.getName())
                    .status(STATUS_WAITING)
                    .build();
            analysis.setTenantId(tenantId);
            analysisMapper.insert(analysis);
            creditService.bindBusiness(creditLogId, analysis.getId());
            businessLogService.info(businessTraceId, "REFERENCE_ANALYSIS", analysis.getId(), "QUEUED", STATUS_WAITING,
                    "对标分析任务已入队", analysis);
            submitQueuedAnalysis(tenantId, analysis.getId());
            return buildResp(analysis);
        } catch (Exception ex) {
            if (creditLogId != null) {
                creditService.refundByLogId(creditLogId, ex.getMessage());
            }
            businessLogService.error(businessTraceId, "REFERENCE_ANALYSIS", null, "FAILED", STATUS_FAILED,
                    StrUtil.format("对标分析失败：{}", ex.getMessage()), reqVO);
            throw ex;
        }
    }

    private TkReferenceAnalysisRespVO regenerateScriptOptionsWithinTenant(TkReferenceAnalysisDO analysis, TkMaterialLibraryDO library,
                                                                          int targetDuration) {
        if (!STATUS_SUCCESS.equals(analysis.getStatus())) {
            throw exception(TK_REFERENCE_ANALYSIS_NOT_EXISTS);
        }
        List<ScriptOption> scriptOptions = generateScriptOptions(analysis, library, targetDuration);
        saveScriptOptions(scriptOptions, analysis, library.getTenantId());
        return buildResp(analysis);
    }

    @Override
    public TkReferenceAnalysisRespVO getLatest(Long libraryId, String sourceUrl, String targetLanguage,
                                               String materialPurpose, String analysisProvider) {
        TkMaterialLibraryDO library = materialLibraryService.validateMaterialLibraryReadable(libraryId);
        String normalizedLanguage = TkLanguageSupport.normalize(targetLanguage);
        String normalizedPurpose = TkGeminiPromptConfig.normalizeMaterialPurpose(materialPurpose);
        String normalizedProvider = TkReferenceAnalysisProvider.normalize(analysisProvider);
        TkReferenceAnalysisRespVO[] result = new TkReferenceAnalysisRespVO[1];
        TenantUtils.execute(library.getTenantId(), () -> {
            TkReferenceAnalysisDO analysis = analysisMapper.selectLatestSuccess(libraryId, sourceUrl, normalizedLanguage,
                    normalizedPurpose, normalizedProvider, dataScopeService.getCurrentScope());
            result[0] = analysis == null ? null : buildResp(analysis);
        });
        return result[0];
    }

    @Override
    public TkReferenceAnalysisRespVO getAnalysis(Long id) {
        TkReferenceAnalysisRespVO respVO = buildResp(validateAnalysisReadable(id));
        enrichCreatorNames(Collections.singletonList(respVO));
        return respVO;
    }

    @Override
    public PageResult<TkReferenceAnalysisRespVO> getAnalysisPage(TkReferenceAnalysisPageReqVO pageReqVO) {
        PageResult<TkReferenceAnalysisDO> pageResult = analysisMapper.selectPage(pageReqVO, dataScopeService.getCurrentScope());
        List<TkReferenceAnalysisRespVO> list = new ArrayList<>();
        for (TkReferenceAnalysisDO analysis : pageResult.getList()) {
            list.add(buildResp(analysis));
        }
        enrichCreatorNames(list);
        return new PageResult<>(list, pageResult.getTotal());
    }

    private void enrichCreatorNames(List<TkReferenceAnalysisRespVO> items) {
        Map<String, String> creatorNameMap = resolveCreatorNameMap(items.stream()
                .map(TkReferenceAnalysisRespVO::getCreator)
                .collect(java.util.stream.Collectors.toSet()));
        items.forEach(item -> item.setCreatorName(creatorNameMap.get(item.getCreator())));
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
                .collect(java.util.stream.Collectors.toSet());
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

    @Override
    public TkReferenceAnalysisDO validateAnalysisReadable(Long id) {
        TkReferenceAnalysisDO analysis = analysisMapper.selectById(id);
        if (analysis == null) {
            throw exception(TK_REFERENCE_ANALYSIS_NOT_EXISTS);
        }
        dataScopeService.validateReadable(analysis.getTenantId(), analysis.getCompanyId(), analysis.getCreator());
        return analysis;
    }

    @Override
    public TkReferenceScriptOptionDO validateScriptOptionReadable(Long id) {
        TkReferenceScriptOptionDO option = scriptOptionMapper.selectById(id);
        if (option == null) {
            throw exception(TK_REFERENCE_SCRIPT_OPTION_NOT_EXISTS);
        }
        dataScopeService.validateReadable(option.getTenantId(), option.getCompanyId(), option.getCreator());
        return option;
    }

    private void submitQueuedAnalysis(Long tenantId, Long analysisId) {
        String key = analysisKey(tenantId, analysisId);
        if (!inFlightAnalyses.add(key)) {
            return;
        }
        executorService.submit(() -> {
            try {
                TenantUtils.execute(tenantId, () -> runQueuedAnalysis(analysisId));
            } finally {
                inFlightAnalyses.remove(key);
            }
        });
    }

    @Scheduled(fixedDelayString = "${tk.reference.queue.scan-delay-ms:10000}", initialDelayString = "${tk.reference.queue.scan-delay-ms:10000}")
    public void submitRecoverableAnalyses() {
        if (executorService == null) {
            return;
        }
        List<TkReferenceAnalysisDO> analyses = analysisMapper.selectRecoverableForQueue(
                LocalDateTime.now().minusSeconds(RECOVERY_STALE_SECONDS), RECOVERY_BATCH_SIZE);
        for (TkReferenceAnalysisDO analysis : analyses) {
            submitQueuedAnalysis(analysis.getTenantId(), analysis.getId());
        }
    }

    private void runQueuedAnalysis(Long analysisId) {
        String businessTraceId = null;
        try {
            TkReferenceAnalysisDO analysis = analysisMapper.selectById(analysisId);
            if (analysis == null || STATUS_SUCCESS.equals(analysis.getStatus()) || STATUS_FAILED.equals(analysis.getStatus())) {
                return;
            }
            businessTraceId = analysis.getBusinessTraceId();
            TkMaterialLibraryDO library = materialLibraryMapper.selectById(analysis.getLibraryId());
            if (library == null) {
                throw exception(TK_MATERIAL_LIBRARY_NOT_EXISTS);
            }
            updateAnalysisStatus(analysisId, STATUS_RUNNING, null);
            businessLogService.info(businessTraceId, "REFERENCE_ANALYSIS", analysisId, "RUNNING", STATUS_RUNNING,
                    "开始执行对标分析", analysis);

            TkReferenceAnalyzeReqVO reqVO = buildAnalyzeReq(analysis);
            AnalysisResult result = generateDraft(reqVO, library);
            applySuccessfulAnalysis(analysis, library, result);
            creditService.settle(REFERENCE_ANALYSIS, analysisId);
            businessLogService.info(businessTraceId, "REFERENCE_ANALYSIS", analysisId, "SUCCESS", STATUS_SUCCESS,
                    StrUtil.format("对标分析完成：{}", analysis.getProductName()), analysis);
        } catch (Exception ex) {
            log.error("[runQueuedAnalysis][analysisId({}) 对标分析异步执行失败]", analysisId, ex);
            failQueuedAnalysis(analysisId, ex);
            creditService.refund(REFERENCE_ANALYSIS, analysisId, ex.getMessage());
            businessLogService.error(businessTraceId, "REFERENCE_ANALYSIS", analysisId, "FAILED", STATUS_FAILED,
                    ex.getMessage(), null);
        }
    }

    private TkReferenceAnalyzeReqVO buildAnalyzeReq(TkReferenceAnalysisDO analysis) {
        TkReferenceAnalyzeReqVO reqVO = new TkReferenceAnalyzeReqVO();
        reqVO.setCompanyId(analysis.getCompanyId());
        reqVO.setLibraryId(analysis.getLibraryId());
        reqVO.setSourceUrl(analysis.getSourceUrl());
        reqVO.setTargetLanguage(analysis.getTargetLanguage());
        reqVO.setReferenceDuration(TkVideoDurationSupport.normalize(analysis.getReferenceDuration()));
        reqVO.setMaterialPurpose(TkGeminiPromptConfig.normalizeMaterialPurpose(analysis.getMaterialPurpose()));
        reqVO.setAnalysisProvider(TkReferenceAnalysisProvider.normalize(analysis.getAnalysisProvider()));
        reqVO.setForceRefresh(true);
        return reqVO;
    }

    private void applySuccessfulAnalysis(TkReferenceAnalysisDO analysis, TkMaterialLibraryDO library,
                                         AnalysisResult result) {
        AnalysisDraft draft = result.draft;
        TkReferenceVideoContent videoContent = result.videoContent;
        analysis.setSourceDomain(extractDomain(analysis.getSourceUrl()));
        analysis.setResolvedVideoUrl(videoContent.getResolvedVideoUrl());
        analysis.setCoverUrl(videoContent.getCoverUrl());
        analysis.setProductName(draft.productName);
        analysis.setVideoDuration(videoContent.getDurationSeconds() == null
                ? draft.videoDuration : videoContent.getDurationSeconds().intValue());
        analysis.setPublishTime(null);
        analysis.setCoreSellingPoints(join(draft.coreSellingPoints));
        analysis.setTargetAudience(join(draft.targetAudience));
        analysis.setUsageScenarios(join(draft.usageScenarios));
        analysis.setVideoStructure(join(draft.videoStructure));
        analysis.setAnalysisResult(JsonUtils.toJsonString(buildAnalysisItems(draft)));
        analysis.setDisplayAnalysisResultZh(JsonUtils.toJsonString(buildDisplayAnalysisItemsZh(draft)));
        analysis.setSellingPoints(JsonUtils.toJsonString(draft.sellingPoints));
        analysis.setDisplaySellingPointsZh(JsonUtils.toJsonString(draft.displaySellingPointsZh));
        analysis.setAnalysisProvider(result.aiResult.getProvider());
        analysis.setAnalysisModel(result.aiResult.getModel());
        analysis.setStatus(STATUS_SUCCESS);
        analysis.setFailReason(null);
        analysisMapper.updateById(analysis);

        saveScriptOptions(draft.scriptOptions, analysis, library.getTenantId());
    }

    private void failQueuedAnalysis(Long analysisId, Exception ex) {
        TkReferenceAnalysisDO existing = analysisMapper.selectById(analysisId);
        if (existing == null) {
            return;
        }
        TkReferenceVideoContent videoContent = extractVideoContent(ex);
        TkReferenceAnalysisDO updateObj = new TkReferenceAnalysisDO();
        updateObj.setId(analysisId);
        if (videoContent != null) {
            updateObj.setResolvedVideoUrl(videoContent.getResolvedVideoUrl());
            updateObj.setCoverUrl(videoContent.getCoverUrl());
            updateObj.setVideoDuration(videoContent.getDurationSeconds() == null
                    ? null : videoContent.getDurationSeconds().intValue());
        }
        updateObj.setStatus(STATUS_FAILED);
        updateObj.setFailReason(StrUtil.maxLength(StrUtil.blankToDefault(ex.getMessage(), "AI 对标分析失败"), 512));
        analysisMapper.updateById(updateObj);
    }

    private void updateAnalysisStatus(Long analysisId, String status, String failReason) {
        TkReferenceAnalysisDO updateObj = new TkReferenceAnalysisDO();
        updateObj.setId(analysisId);
        updateObj.setStatus(status);
        updateObj.setFailReason(failReason);
        analysisMapper.updateById(updateObj);
    }

    private String analysisKey(Long tenantId, Long analysisId) {
        return tenantId + ":" + analysisId;
    }

    @PreDestroy
    public void destroy() {
        if (executorService != null) {
            executorService.shutdown();
        }
    }

    private AnalysisResult generateDraft(TkReferenceAnalyzeReqVO reqVO, TkMaterialLibraryDO library) {
        TkReferenceVideoContent videoContent = null;
        try {
            videoContent = referenceVideoContentService.analyze(reqVO.getSourceUrl(), library.getId());
            String prompt = buildAnalysisPrompt(reqVO, library, videoContent);
            List<TkAiImageInput> images = new ArrayList<>();
            for (TkReferenceVideoContent.Frame frame : videoContent.getFrames()) {
                images.add(new TkAiImageInput(frame.getMimeType(), frame.getBase64Data()));
            }
            TkReferenceAiAnalysisResult aiResult = referenceAiAnalysisRouter.analyze(reqVO.getAnalysisProvider(),
                    new TkReferenceAiAnalysisContext(prompt, videoContent.getResolvedVideoUrl(), images));
            return new AnalysisResult(parseDraft(aiResult.getContent(), reqVO, library,
                    videoDurationFallback(videoContent)), videoContent, aiResult);
        } catch (Exception ex) {
            log.warn("[generateDraft][sourceUrl({}) AI 分析失败]", reqVO.getSourceUrl(), ex);
            throw new ReferenceAnalysisException(exception(TK_REFERENCE_AI_ANALYSIS_FAILED, ex.getMessage()), videoContent);
        }
    }

    private TkReferenceAnalysisDO saveFailedAnalysis(String businessTraceId, TkReferenceAnalyzeReqVO reqVO,
                                                     TkMaterialLibraryDO library, Long companyId, Long tenantId,
                                                     Exception ex) {
        TkReferenceVideoContent videoContent = extractVideoContent(ex);
        TkReferenceAnalysisDO analysis = TkReferenceAnalysisDO.builder()
                .businessTraceId(businessTraceId)
                .companyId(companyId)
                .libraryId(reqVO.getLibraryId())
                .sourceUrl(reqVO.getSourceUrl())
                .targetLanguage(reqVO.getTargetLanguage())
                .referenceDuration(reqVO.getReferenceDuration())
                .materialPurpose(reqVO.getMaterialPurpose())
                .analysisProvider(reqVO.getAnalysisProvider())
                .sourceDomain(extractDomain(reqVO.getSourceUrl()))
                .resolvedVideoUrl(videoContent == null ? null : videoContent.getResolvedVideoUrl())
                .coverUrl(videoContent == null ? null : videoContent.getCoverUrl())
                .videoDuration(videoContent == null || videoContent.getDurationSeconds() == null
                        ? null : videoContent.getDurationSeconds().intValue())
                .productName(library.getName())
                .status(STATUS_FAILED)
                .failReason(StrUtil.maxLength(StrUtil.blankToDefault(ex.getMessage(), "AI 对标分析失败"), 512))
                .build();
        analysis.setTenantId(tenantId);
        analysisMapper.insert(analysis);
        return analysis;
    }

    private TkReferenceVideoContent extractVideoContent(Exception ex) {
        return ex instanceof ReferenceAnalysisException ? ((ReferenceAnalysisException) ex).videoContent : null;
    }

    private Long resolveCompanyId(Long requestedCompanyId, TkMaterialLibraryDO library, TkUserScope scope) {
        if (requestedCompanyId == null) {
            return library.getCompanyId();
        }
        return dataScopeService.getWritableCompanyId(requestedCompanyId);
    }

    private String buildAnalysisPrompt(TkReferenceAnalyzeReqVO reqVO, TkMaterialLibraryDO library,
                                       TkReferenceVideoContent videoContent) {
        return StrUtil.format(withAnalysisLanguageInstruction(getGeminiPrompt(
                        TkGeminiPromptConfig.analysisPromptKey(reqVO.getMaterialPurpose()),
                        TkGeminiPromptConfig.defaultAnalysisPrompt(reqVO.getMaterialPurpose())), reqVO.getTargetLanguage()),
                reqVO.getSourceUrl(),
                videoContent.getResolvedVideoUrl(),
                videoContent.getDurationSeconds(),
                videoContent.getResolution(),
                frameSecondsText(videoContent.getFrames()),
                library.getName(),
                StrUtil.blankToDefault(library.getCategory(), "未分类"),
                StrUtil.blankToDefault(library.getScene(), "带货混剪"),
                StrUtil.blankToDefault(library.getTags(), "产品卖点"),
                TkVideoDurationSupport.normalize(reqVO.getReferenceDuration()));
    }

    private List<ScriptOption> generateScriptOptions(TkReferenceAnalysisDO analysis, TkMaterialLibraryDO library, int targetDuration) {
        try {
            return parseScriptOptions(geminiClient.generateText(buildScriptRegenerationPrompt(analysis, library, targetDuration)));
        } catch (Exception ex) {
            log.warn("[generateScriptOptions][analysisId({}) sourceUrl({}) AI 文案生成失败]",
                    analysis.getId(), analysis.getSourceUrl(), ex);
            throw exception(TK_REFERENCE_AI_ANALYSIS_FAILED, ex.getMessage());
        }
    }

    private String buildScriptRegenerationPrompt(TkReferenceAnalysisDO analysis, TkMaterialLibraryDO library, int targetDuration) {
        return StrUtil.format(withLanguageInstruction(getGeminiPrompt(
                        TkGeminiPromptConfig.scriptRegenerationPromptKey(analysis.getMaterialPurpose()),
                        TkGeminiPromptConfig.defaultScriptRegenerationPrompt(analysis.getMaterialPurpose())),
                        analysis.getTargetLanguage()),
                analysis.getSourceUrl(),
                StrUtil.blankToDefault(analysis.getProductName(), "未识别具体商品"),
                analysis.getVideoDuration() == null ? TkVideoDurationSupport.DEFAULT_TARGET_DURATION : analysis.getVideoDuration(),
                targetDuration,
                StrUtil.blankToDefault(analysis.getCoreSellingPoints(), "-"),
                StrUtil.blankToDefault(analysis.getTargetAudience(), "-"),
                StrUtil.blankToDefault(analysis.getUsageScenarios(), "-"),
                StrUtil.blankToDefault(analysis.getVideoStructure(), "-"),
                StrUtil.blankToDefault(analysis.getSellingPoints(), "[]"),
                library.getName(),
                StrUtil.blankToDefault(library.getCategory(), "未分类"),
                StrUtil.blankToDefault(library.getScene(), "带货混剪"),
                StrUtil.blankToDefault(library.getTags(), "产品卖点"));
    }

    private String getGeminiPrompt(String configKey, String defaultValue) {
        return apiKeyConfigService.getValueOrDefault(TkGeminiPromptConfig.PROVIDER, configKey, defaultValue);
    }

    private String withLanguageInstruction(String prompt, String targetLanguage) {
        return prompt + "\n\n" + TkLanguageSupport.promptInstruction(targetLanguage)
                + "\nscriptOptions 中 title、points、scriptText 必须使用目标文案语言，用于配音和字幕。"
                + "\nscriptOptions 中必须同时返回 displayTitleZh、displayPointsZh、displayScriptZh，三者必须使用简体中文，用于后台展示；如果目标文案语言是中文，可以与原字段相同。"
                + "\n不要把 displayTitleZh、displayPointsZh、displayScriptZh 用作口播原文，口播原文只放在 scriptText。\n";
    }

    private String withAnalysisLanguageInstruction(String prompt, String targetLanguage) {
        return withLanguageInstruction(prompt, targetLanguage)
                + "\nproductName、coreSellingPoints、targetAudience、usageScenarios、videoStructure、sellingPoints 必须使用目标文案语言，作为原文展示内容。"
                + "\n必须额外返回 displayProductNameZh、displayCoreSellingPointsZh、displayTargetAudienceZh、displayUsageScenariosZh、displayVideoStructureZh、displaySellingPointsZh，全部使用简体中文，作为中文展示内容。"
                + "\ndisplaySellingPointsZh 的数组长度和 sellingPoints 保持一致，每项包含 title、desc、count、badge；count 可以与原文卖点一致。\n";
    }

    private String frameSecondsText(List<TkReferenceVideoContent.Frame> frames) {
        List<String> seconds = new ArrayList<>();
        for (TkReferenceVideoContent.Frame frame : frames) {
            seconds.add(frame.getSecond() + "s");
        }
        return String.join("、", seconds);
    }

    private AnalysisDraft parseDraft(String content, TkReferenceAnalyzeReqVO reqVO, TkMaterialLibraryDO library,
                                     Integer videoDurationFallback) {
        String json = stripJson(content);
        JsonNode root = JsonUtils.parseTree(json);
        AnalysisDraft draft = new AnalysisDraft();
        draft.productName = textOrDefault(root, "未识别具体商品", "productName", "displayProductNameZh");
        draft.videoDuration = flexibleDurationSeconds(root, "videoDuration", videoDurationFallback);
        draft.publishTime = optionalText(root, "publishTime");
        draft.displayProductNameZh = optionalText(root, "displayProductNameZh");
        draft.displayCoreSellingPointsZh = optionalStringList(root.path("displayCoreSellingPointsZh"));
        draft.displayTargetAudienceZh = optionalStringList(root.path("displayTargetAudienceZh"));
        draft.displayUsageScenariosZh = optionalStringList(root.path("displayUsageScenariosZh"));
        draft.displayVideoStructureZh = optionalStringList(root.path("displayVideoStructureZh"));
        draft.coreSellingPoints = flexibleStringList(root, "coreSellingPoints", draft.displayCoreSellingPointsZh, "未提取核心卖点");
        draft.targetAudience = flexibleStringList(root, "targetAudience", draft.displayTargetAudienceZh, "未提取目标人群");
        draft.usageScenarios = flexibleStringList(root, "usageScenarios", draft.displayUsageScenariosZh, "未提取使用场景");
        draft.videoStructure = flexibleStringList(root, "videoStructure", draft.displayVideoStructureZh, "未提取视频结构");
        draft.sellingPoints = flexibleSellingPoints(root.path("sellingPoints"), draft.coreSellingPoints);
        draft.displaySellingPointsZh = optionalSellingPoints(root.path("displaySellingPointsZh"), draft.sellingPoints);
        draft.scriptOptions = requiredScriptOptions(root.path("scriptOptions"));
        validateDraftSize(draft);
        return draft;
    }

    private List<ScriptOption> parseScriptOptions(String content) {
        String json = stripJson(content);
        JsonNode root = JsonUtils.parseTree(json);
        List<ScriptOption> scriptOptions = requiredScriptOptions(root.path("scriptOptions"));
        return scriptOptions;
    }

    private List<TkReferenceScriptOptionDO> buildScriptOptions(List<ScriptOption> scriptOptions, TkReferenceAnalysisDO analysis, Long tenantId) {
        List<TkReferenceScriptOptionDO> options = new ArrayList<>();
        for (int i = 0; i < Math.min(SCRIPT_OPTION_COUNT, scriptOptions.size()); i++) {
            TkReferenceScriptOptionDO optionDO = buildScriptOption(scriptOptions.get(i), analysis, tenantId, i);
            options.add(optionDO);
        }
        return options;
    }

    private TkReferenceScriptOptionDO buildScriptOption(ScriptOption option, TkReferenceAnalysisDO analysis, Long tenantId, int index) {
        TkReferenceScriptOptionDO optionDO = TkReferenceScriptOptionDO.builder()
                .analysisId(analysis.getId())
                .companyId(analysis.getCompanyId())
                .libraryId(analysis.getLibraryId())
                .optionNo(index + 1)
                .title(StrUtil.maxLength(option.getTitle(), 255))
                .points(StrUtil.maxLength(option.getPoints(), 255))
                .displayTitleZh(StrUtil.maxLength(option.getDisplayTitleZh(), 255))
                .displayPointsZh(StrUtil.maxLength(option.getDisplayPointsZh(), 255))
                .estimatedConversionRate(option.getEstimatedConversionRate())
                .conversionLevel(StrUtil.blankToDefault(option.getConversionLevel(), index < 2 ? "高" : "中"))
                .scriptText(option.getScriptText())
                .segmentTimeline(option.getSegmentTimeline())
                .displayScriptZh(option.getDisplayScriptZh())
                .selected(index == 0)
                .build();
        optionDO.setTenantId(tenantId);
        return optionDO;
    }

    private void saveScriptOptions(List<ScriptOption> scriptOptions, TkReferenceAnalysisDO analysis, Long tenantId) {
        List<TkReferenceScriptOptionDO> existingOptions = scriptOptionMapper.selectListByAnalysisId(analysis.getId());
        for (int i = 0; i < Math.min(SCRIPT_OPTION_COUNT, scriptOptions.size()); i++) {
            ScriptOption option = scriptOptions.get(i);
            if (i < existingOptions.size()) {
                TkReferenceScriptOptionDO optionDO = existingOptions.get(i);
                optionDO.setOptionNo(i + 1);
                optionDO.setTitle(StrUtil.maxLength(option.getTitle(), 255));
                optionDO.setPoints(StrUtil.maxLength(option.getPoints(), 255));
                optionDO.setDisplayTitleZh(StrUtil.maxLength(option.getDisplayTitleZh(), 255));
                optionDO.setDisplayPointsZh(StrUtil.maxLength(option.getDisplayPointsZh(), 255));
                optionDO.setEstimatedConversionRate(option.getEstimatedConversionRate());
                optionDO.setConversionLevel(StrUtil.blankToDefault(option.getConversionLevel(), i < 2 ? "高" : "中"));
                optionDO.setScriptText(option.getScriptText());
                optionDO.setSegmentTimeline(option.getSegmentTimeline());
                optionDO.setDisplayScriptZh(option.getDisplayScriptZh());
                optionDO.setSelected(i == 0);
                scriptOptionMapper.updateById(optionDO);
                continue;
            }
            TkReferenceScriptOptionDO optionDO = buildScriptOption(option, analysis, tenantId, i);
            scriptOptionMapper.insert(optionDO);
        }
    }

    private TkReferenceAnalysisRespVO buildResp(TkReferenceAnalysisDO analysis) {
        TkReferenceAnalysisRespVO respVO = BeanUtils.toBean(analysis, TkReferenceAnalysisRespVO.class);
        List<TkReferenceScriptOptionRespVO> options = BeanUtils.toBean(
                scriptOptionMapper.selectListByAnalysisId(analysis.getId()),
                TkReferenceScriptOptionRespVO.class);
        int sellingPointCount = countSellingPoints(analysis.getSellingPoints());
        int scriptOptionCount = options.size();
        respVO.setScriptOptions(options);
        respVO.setSellingPointCount(sellingPointCount);
        respVO.setScriptOptionCount(scriptOptionCount);
        respVO.setAnalysisStageStatus(resolveAnalysisStageStatus(analysis));
        respVO.setSellingPointStageStatus(resolveDependentStageStatus(analysis, sellingPointCount));
        respVO.setScriptStageStatus(resolveDependentStageStatus(analysis, scriptOptionCount));
        return respVO;
    }

    private String resolveAnalysisStageStatus(TkReferenceAnalysisDO analysis) {
        if (STATUS_FAILED.equals(analysis.getStatus())) {
            return STATUS_FAILED;
        }
        if (STATUS_RUNNING.equals(analysis.getStatus())) {
            return STATUS_RUNNING;
        }
        if (STATUS_WAITING.equals(analysis.getStatus())) {
            return STATUS_WAITING;
        }
        return STATUS_SUCCESS;
    }

    private String resolveDependentStageStatus(TkReferenceAnalysisDO analysis, int count) {
        if (STATUS_FAILED.equals(analysis.getStatus())) {
            return STATUS_FAILED;
        }
        if (!STATUS_SUCCESS.equals(analysis.getStatus())) {
            return STATUS_WAITING;
        }
        return count > 0 ? STATUS_SUCCESS : STATUS_WAITING;
    }

    private int countSellingPoints(String sellingPoints) {
        if (StrUtil.isBlank(sellingPoints)) {
            return 0;
        }
        try {
            JsonNode node = JsonUtils.parseTree(sellingPoints);
            return node != null && node.isArray() ? node.size() : 0;
        } catch (Exception ex) {
            log.warn("[countSellingPoints][sellingPoints({}) 解析失败]", sellingPoints, ex);
            return 0;
        }
    }

    private boolean isLegacyFallbackAnalysis(TkReferenceAnalysisDO analysis) {
        if (StrUtil.isBlank(analysis.getResolvedVideoUrl()) || StrUtil.isBlank(analysis.getCoverUrl())) {
            return true;
        }
        if (containsPlaceholder(analysis.getCoreSellingPoints())
                || containsPlaceholder(analysis.getTargetAudience())
                || containsPlaceholder(analysis.getUsageScenarios())
                || containsPlaceholder(analysis.getVideoStructure())
                || containsPlaceholder(analysis.getAnalysisResult())
                || containsPlaceholder(analysis.getSellingPoints())) {
            return true;
        }
        return scriptOptionMapper.selectListByAnalysisId(analysis.getId()).stream()
                .anyMatch(option -> containsPlaceholder(option.getTitle())
                        || containsPlaceholder(option.getPoints())
                        || containsPlaceholder(option.getScriptText()));
    }

    private boolean containsPlaceholder(String value) {
        return value != null && value.contains("??");
    }

    private List<String> buildAnalysisItems(AnalysisDraft draft) {
        return Arrays.asList(
                StrUtil.format("识别到产品：{}", draft.productName),
                StrUtil.format("核心卖点：{}", join(draft.coreSellingPoints)),
                StrUtil.format("目标人群：{}", join(draft.targetAudience)),
                StrUtil.format("使用场景：{}", join(draft.usageScenarios)),
                StrUtil.format("视频结构：{}", String.join(" → ", draft.videoStructure))
        );
    }

    private List<String> buildDisplayAnalysisItemsZh(AnalysisDraft draft) {
        return Arrays.asList(
                StrUtil.format("识别到产品：{}", StrUtil.blankToDefault(draft.displayProductNameZh, draft.productName)),
                StrUtil.format("核心卖点：{}", joinOrFallback(draft.displayCoreSellingPointsZh, draft.coreSellingPoints)),
                StrUtil.format("目标人群：{}", joinOrFallback(draft.displayTargetAudienceZh, draft.targetAudience)),
                StrUtil.format("使用场景：{}", joinOrFallback(draft.displayUsageScenariosZh, draft.usageScenarios)),
                StrUtil.format("视频结构：{}", joinOrFallback(draft.displayVideoStructureZh, draft.videoStructure, " → "))
        );
    }

    private void validateDraftSize(AnalysisDraft draft) {
        if (draft.scriptOptions.isEmpty()) {
            throw new IllegalStateException("AI 返回字段 scriptOptions 为空");
        }
    }

    private String stripJson(String content) {
        String trimmed = StrUtil.trim(content);
        if (trimmed.startsWith("```")) {
            int first = trimmed.indexOf('\n');
            int last = trimmed.lastIndexOf("```");
            if (first >= 0 && last > first) {
                trimmed = trimmed.substring(first + 1, last).trim();
            }
        }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    private String extractDomain(String sourceUrl) {
        String normalized = extractSupportedSourceUrl(sourceUrl);
        try {
            return StrUtil.blankToDefault(URI.create(normalized).getHost(), "www.tiktok.com");
        } catch (Exception ignored) {
            return "www.tiktok.com";
        }
    }

    private String extractSupportedSourceUrl(String sourceUrl) {
        String text = StrUtil.trimToEmpty(sourceUrl);
        if (StrUtil.isBlank(text)) {
            return text;
        }
        java.util.regex.Matcher matcher = SOURCE_URL_PATTERN.matcher(text);
        while (matcher.find()) {
            String candidate = stripTrailingUrlPunctuation(matcher.group());
            if (isSupportedSourceUrl(candidate)) {
                return candidate;
            }
        }
        return text;
    }

    private boolean isSupportedSourceUrl(String url) {
        try {
            URI uri = URI.create(url);
            String host = StrUtil.blankToDefault(uri.getHost(), "").toLowerCase();
            String path = StrUtil.blankToDefault(uri.getPath(), "").toLowerCase();
            return StrUtil.containsAny(host, "douyin.com", "tiktok.com", "bilibili.com", "b23.tv")
                    || path.endsWith(".mp4") || path.endsWith(".mov") || path.endsWith(".webm") || path.endsWith(".m4v");
        } catch (Exception ex) {
            return false;
        }
    }

    private String stripTrailingUrlPunctuation(String url) {
        String result = StrUtil.trimToEmpty(url);
        while (StrUtil.isNotBlank(result) && StrUtil.containsAny(String.valueOf(result.charAt(result.length() - 1)),
                ".", ",", ";", ":", "!", "?", ")", "]", "}", "）", "】", "》", "\"", "'")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private String join(List<String> values) {
        return String.join("｜", values);
    }

    private String joinOrFallback(List<String> values, List<String> fallback) {
        return joinOrFallback(values, fallback, "｜");
    }

    private String joinOrFallback(List<String> values, List<String> fallback, String delimiter) {
        List<String> selected = values == null || values.isEmpty() ? fallback : values;
        return selected == null || selected.isEmpty() ? "-" : String.join(delimiter, selected);
    }

    private String requiredText(JsonNode root, String field) {
        String value = root.path(field).asText();
        if (StrUtil.isBlank(value)) {
            throw new IllegalStateException(StrUtil.format("AI 返回缺少字段 {}", field));
        }
        return value;
    }

    private String requiredText(JsonNode root, String field, String... fallbackFields) {
        String value = optionalText(root, field);
        if (StrUtil.isNotBlank(value)) {
            return value;
        }
        for (String fallbackField : fallbackFields) {
            value = optionalText(root, fallbackField);
            if (StrUtil.isNotBlank(value)) {
                return value;
            }
        }
        throw new IllegalStateException(StrUtil.format("AI 返回缺少字段 {}", field));
    }

    private String optionalText(JsonNode root, String field) {
        String value = flexibleText(root.path(field));
        return StrUtil.blankToDefault(value, null);
    }

    private String textOrDefault(JsonNode root, String defaultValue, String... fields) {
        String value = firstNonBlankText(root, fields);
        return StrUtil.blankToDefault(value, defaultValue);
    }

    private String firstNonBlankText(JsonNode root, String... fields) {
        for (String field : fields) {
            String value = optionalText(root, field);
            if (StrUtil.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private String flexibleText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isArray()) {
            List<String> values = stringListValues(node);
            return values == null || values.isEmpty() ? null : join(values);
        }
        if (node.isObject()) {
            String value = firstNonBlankText(node, "title", "desc", "text", "content", "summary", "points");
            return StrUtil.blankToDefault(value, JsonUtils.toJsonString(node));
        }
        return StrUtil.blankToDefault(node.asText(), null);
    }

    private Integer requiredInteger(JsonNode root, String field) {
        JsonNode node = root.path(field);
        Integer value = flexibleInteger(node);
        if (value == null) {
            throw invalidNumber(field);
        }
        return value;
    }

    private Integer flexibleDurationSeconds(JsonNode root, String field, Integer fallback) {
        JsonNode node = root.path(field);
        Integer value = flexibleInteger(node);
        if (value != null) {
            return value;
        }
        if (node.isMissingNode() || node.isNull()) {
            return durationFallbackOrThrow(field, fallback);
        }
        String text = node.asText();
        if (StrUtil.isBlank(text)) {
            return durationFallbackOrThrow(field, fallback);
        }
        java.util.regex.Matcher matcher = DURATION_SECONDS_PATTERN.matcher(text);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group());
        }
        return durationFallbackOrThrow(field, fallback);
    }

    private Integer flexibleInteger(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.asInt();
        }
        String text = node.asText();
        if (StrUtil.isBlank(text)) {
            return null;
        }
        java.util.regex.Matcher matcher = NUMBER_TEXT_PATTERN.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        return BigDecimal.valueOf(Double.parseDouble(matcher.group())).setScale(0, RoundingMode.DOWN).intValue();
    }

    private Integer optionalInteger(JsonNode node, int fallback) {
        Integer value = flexibleInteger(node);
        return value == null ? fallback : value;
    }

    private BigDecimal flexibleDecimal(JsonNode node, String field) {
        if (node != null && node.isNumber()) {
            return BigDecimal.valueOf(node.asDouble()).setScale(2, RoundingMode.HALF_UP);
        }
        String text = node == null ? null : node.asText();
        if (StrUtil.isNotBlank(text)) {
            java.util.regex.Matcher matcher = NUMBER_TEXT_PATTERN.matcher(text);
            if (matcher.find()) {
                return new BigDecimal(matcher.group()).setScale(2, RoundingMode.HALF_UP);
            }
        }
        throw invalidNumber(field);
    }

    private IllegalStateException invalidNumber(String field) {
        return new IllegalStateException(StrUtil.format("AI 返回字段 {} 不是数字", field));
    }

    private Integer durationFallbackOrThrow(String field, Integer fallback) {
        if (fallback != null) {
            return fallback;
        }
        throw new IllegalStateException(StrUtil.format("AI 返回字段 {} 不是数字", field));
    }

    private Integer videoDurationFallback(TkReferenceVideoContent videoContent) {
        return videoContent == null || videoContent.getDurationSeconds() == null
                ? null : videoContent.getDurationSeconds().intValue();
    }

    private List<String> requiredStringList(JsonNode node, String field) {
        List<String> values = stringListValues(node);
        if (values == null) {
            throw new IllegalStateException(StrUtil.format("AI 返回字段 {} 不是数组", field));
        }
        if (values.isEmpty()) {
            throw new IllegalStateException(StrUtil.format("AI 返回字段 {} 为空", field));
        }
        return values;
    }

    private List<String> flexibleStringList(JsonNode root, String field, List<String> fallback, String defaultValue) {
        List<String> values = stringListValues(root.path(field));
        if (values != null && !values.isEmpty()) {
            return values;
        }
        if (fallback != null && !fallback.isEmpty()) {
            return fallback;
        }
        return new ArrayList<>(Collections.singletonList(defaultValue));
    }

    private List<String> optionalStringList(JsonNode node) {
        List<String> values = stringListValues(node);
        if (values == null) {
            return null;
        }
        return values.isEmpty() ? null : values;
    }

    private List<String> stringListValues(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return new ArrayList<>();
        }
        if (node.isArray()) {
            List<String> values = new ArrayList<>();
            node.forEach(item -> {
                if (StrUtil.isNotBlank(item.asText())) {
                    values.add(StrUtil.trim(item.asText()));
                }
            });
            return values;
        }
        if (!node.isTextual()) {
            return null;
        }
        return splitStringList(node.asText());
    }

    private List<String> splitStringList(String text) {
        List<String> values = new ArrayList<>();
        String trimmed = StrUtil.trimToEmpty(text);
        if (StrUtil.isBlank(trimmed)) {
            return values;
        }
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            try {
                List<String> parsed = stringListValues(JsonUtils.parseTree(trimmed));
                if (parsed != null && !parsed.isEmpty()) {
                    return parsed;
                }
            } catch (Exception ignored) {
                // Treat it as plain text below.
            }
        }
        for (String item : STRING_LIST_SPLIT_PATTERN.split(trimmed)) {
            String value = StrUtil.trim(item);
            if (StrUtil.isNotBlank(value)) {
                values.add(value);
            }
        }
        return values;
    }

    private List<SellingPoint> requiredSellingPoints(JsonNode node) {
        if (!node.isArray()) {
            throw new IllegalStateException("AI 返回字段 sellingPoints 不是数组");
        }
        List<SellingPoint> values = new ArrayList<>();
        node.forEach(item -> values.add(new SellingPoint(
                requiredText(item, "title"),
                requiredText(item, "desc"),
                requiredInteger(item, "count"),
                requiredText(item, "badge"))));
        if (values.isEmpty()) {
            throw new IllegalStateException("AI 返回字段 sellingPoints 为空");
        }
        return values;
    }

    private List<SellingPoint> flexibleSellingPoints(JsonNode node, List<String> fallbackPoints) {
        if (node != null && node.isArray()) {
            List<SellingPoint> values = new ArrayList<>();
            node.forEach(item -> values.add(new SellingPoint(
                    textOrDefault(item, "卖点", "title", "name", "point"),
                    textOrDefault(item, textOrDefault(item, "卖点描述", "title", "name", "point"), "desc", "description", "summary", "content"),
                    optionalInteger(item.path("count"), 0),
                    textOrDefault(item, "核心卖点", "badge", "tag", "level"))));
            values.removeIf(item -> StrUtil.isBlank(item.getTitle()) && StrUtil.isBlank(item.getDesc()));
            if (!values.isEmpty()) {
                return values;
            }
        }
        List<String> points = fallbackPoints == null || fallbackPoints.isEmpty()
                ? Collections.singletonList("未提取核心卖点") : fallbackPoints;
        List<SellingPoint> values = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            String point = StrUtil.blankToDefault(points.get(i), "未提取核心卖点");
            values.add(new SellingPoint(point, point, i + 1, "核心卖点"));
        }
        return values;
    }

    private List<SellingPoint> optionalSellingPoints(JsonNode node, List<SellingPoint> fallback) {
        if (node == null || !node.isArray()) {
            return fallback;
        }
        List<SellingPoint> values = new ArrayList<>();
        node.forEach(item -> values.add(new SellingPoint(
                StrUtil.blankToDefault(optionalText(item, "title"), ""),
                StrUtil.blankToDefault(optionalText(item, "desc"), ""),
                optionalInteger(item.path("count"), 0),
                StrUtil.blankToDefault(optionalText(item, "badge"), ""))));
        values.removeIf(item -> StrUtil.isBlank(item.getTitle()) && StrUtil.isBlank(item.getDesc()));
        return values.isEmpty() ? fallback : values;
    }

    private List<ScriptOption> requiredScriptOptions(JsonNode node) {
        if (!node.isArray()) {
            throw new IllegalStateException("AI 返回字段 scriptOptions 不是数组");
        }
        List<ScriptOption> values = new ArrayList<>();
        node.forEach(item -> values.add(normalizeScriptOption(item, values.size())));
        if (values.isEmpty()) {
            throw new IllegalStateException("AI 返回字段 scriptOptions 为空");
        }
        return values;
    }

    private ScriptOption normalizeScriptOption(JsonNode item, int index) {
        String scriptText = firstNonBlankText(item, "scriptText", "displayScriptZh", "content", "copy", "text", "voiceover", "caption");
        String title = firstNonBlankText(item, "title", "displayTitleZh", "headline", "name");
        title = StrUtil.blankToDefault(title, scriptOptionTitleFallback(scriptText, index));
        String points = firstNonBlankText(item, "points", "displayPointsZh", "sellingPoints", "keyPoints", "summary", "benefits", "reason");
        points = StrUtil.blankToDefault(points, scriptOptionPointsFallback(title, scriptText));
        scriptText = StrUtil.blankToDefault(scriptText, scriptOptionScriptFallback(title, points));
        String displayTitleZh = StrUtil.blankToDefault(optionalText(item, "displayTitleZh"), title);
        String displayPointsZh = StrUtil.blankToDefault(optionalText(item, "displayPointsZh"), points);
        String displayScriptZh = StrUtil.blankToDefault(optionalText(item, "displayScriptZh"), scriptText);
        return new ScriptOption(
                title,
                points,
                displayTitleZh,
                displayPointsZh,
                flexibleDecimalOrNull(item.path("estimatedConversionRate")),
                StrUtil.blankToDefault(firstNonBlankText(item, "conversionLevel", "level"), defaultConversionLevel(index)),
                scriptText,
                optionalJson(item.path("segmentTimeline")),
                displayScriptZh);
    }

    private String scriptOptionTitleFallback(String scriptText, int index) {
        if (StrUtil.isNotBlank(scriptText)) {
            return StrUtil.maxLength(scriptText, 60);
        }
        return "文案方案" + (index + 1);
    }

    private String scriptOptionPointsFallback(String title, String scriptText) {
        if (StrUtil.isNotBlank(scriptText)) {
            return StrUtil.maxLength(scriptText, 80);
        }
        return title;
    }

    private String scriptOptionScriptFallback(String title, String points) {
        return StrUtil.format("{}。{}", title, points);
    }

    private String defaultConversionLevel(int index) {
        return index < 2 ? "高" : "中";
    }

    private BigDecimal flexibleDecimalOrNull(JsonNode node) {
        try {
            return flexibleDecimal(node, "estimatedConversionRate");
        } catch (IllegalStateException ex) {
            return null;
        }
    }

    private String optionalJson(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        return JsonUtils.toJsonString(node);
    }

    private static class AnalysisResult {
        private final AnalysisDraft draft;
        private final TkReferenceVideoContent videoContent;
        private final TkReferenceAiAnalysisResult aiResult;

        private AnalysisResult(AnalysisDraft draft, TkReferenceVideoContent videoContent,
                               TkReferenceAiAnalysisResult aiResult) {
            this.draft = draft;
            this.videoContent = videoContent;
            this.aiResult = aiResult;
        }
    }

    private static class AnalysisDraft {
        private String productName;
        private Integer videoDuration;
        private String publishTime;
        private List<String> coreSellingPoints;
        private List<String> targetAudience;
        private List<String> usageScenarios;
        private List<String> videoStructure;
        private List<SellingPoint> sellingPoints;
        private String displayProductNameZh;
        private List<String> displayCoreSellingPointsZh;
        private List<String> displayTargetAudienceZh;
        private List<String> displayUsageScenariosZh;
        private List<String> displayVideoStructureZh;
        private List<SellingPoint> displaySellingPointsZh;
        private List<ScriptOption> scriptOptions;
    }

    private static class SellingPoint {

        private final String title;
        private final String desc;
        private final Integer count;
        private final String badge;

        private SellingPoint(String title, String desc, Integer count, String badge) {
            this.title = title;
            this.desc = desc;
            this.count = count;
            this.badge = badge;
        }

        public String getTitle() {
            return title;
        }

        public String getDesc() {
            return desc;
        }

        public Integer getCount() {
            return count;
        }

        public String getBadge() {
            return badge;
        }

    }

    private static class ScriptOption {

        private final String title;
        private final String points;
        private final String displayTitleZh;
        private final String displayPointsZh;
        private final BigDecimal estimatedConversionRate;
        private final String conversionLevel;
        private final String scriptText;
        private final String segmentTimeline;
        private final String displayScriptZh;

        private ScriptOption(String title, String points, String displayTitleZh, String displayPointsZh,
                             BigDecimal estimatedConversionRate, String conversionLevel, String scriptText,
                             String segmentTimeline, String displayScriptZh) {
            this.title = title;
            this.points = points;
            this.displayTitleZh = StrUtil.blankToDefault(displayTitleZh, title);
            this.displayPointsZh = StrUtil.blankToDefault(displayPointsZh, points);
            this.estimatedConversionRate = estimatedConversionRate;
            this.conversionLevel = conversionLevel;
            this.scriptText = scriptText;
            this.segmentTimeline = segmentTimeline;
            this.displayScriptZh = StrUtil.blankToDefault(displayScriptZh, scriptText);
        }

        public String getTitle() {
            return title;
        }

        public String getPoints() {
            return points;
        }

        public String getDisplayTitleZh() {
            return displayTitleZh;
        }

        public String getDisplayPointsZh() {
            return displayPointsZh;
        }

        public BigDecimal getEstimatedConversionRate() {
            return estimatedConversionRate;
        }

        public String getConversionLevel() {
            return conversionLevel;
        }

        public String getScriptText() {
            return scriptText;
        }

        public String getSegmentTimeline() {
            return segmentTimeline;
        }

        public String getDisplayScriptZh() {
            return displayScriptZh;
        }

    }

    private static class ReferenceAnalysisException extends RuntimeException {

        private final ServiceException serviceException;
        private final TkReferenceVideoContent videoContent;

        private ReferenceAnalysisException(ServiceException serviceException, TkReferenceVideoContent videoContent) {
            super(serviceException.getMessage(), serviceException);
            this.serviceException = serviceException;
            this.videoContent = videoContent;
        }

    }

}

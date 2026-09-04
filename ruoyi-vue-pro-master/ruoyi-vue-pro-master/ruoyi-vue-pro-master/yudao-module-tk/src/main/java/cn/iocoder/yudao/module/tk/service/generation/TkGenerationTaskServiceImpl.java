package cn.iocoder.yudao.module.tk.service.generation;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.tk.controller.admin.generation.vo.TkGenerationPrecheckRespVO;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.tk.controller.admin.generation.vo.TkGenerationTaskCreateReqVO;
import cn.iocoder.yudao.module.tk.controller.admin.generation.vo.TkGenerationTaskPageReqVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkBgmAssetDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationTaskDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationBatchDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkMaterialLibraryDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkReferenceAnalysisDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkReferenceScriptOptionDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkGenerationTaskMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.TkGenerationBatchMapper;
import cn.iocoder.yudao.module.tk.enums.TkGenerationStatusEnum;
import cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties;
import cn.iocoder.yudao.module.tk.service.bgm.TkBgmAssetService;
import cn.iocoder.yudao.module.tk.service.credit.TkCreditService;
import cn.iocoder.yudao.module.tk.service.generation.pipeline.TkGeminiPromptConfig;
import cn.iocoder.yudao.module.tk.service.generation.pipeline.TkMimoVoiceModeEnum;
import cn.iocoder.yudao.module.tk.service.generation.pipeline.TkLanguageSupport;
import cn.iocoder.yudao.module.tk.service.generation.pipeline.TkGenerationPipelineService;
import cn.iocoder.yudao.module.tk.service.generation.pipeline.TkNativeOpeningSupport;
import cn.iocoder.yudao.module.tk.service.generation.pipeline.TkVideoDurationSupport;
import cn.iocoder.yudao.module.tk.service.generation.pipeline.TkTtsProviderEnum;
import cn.iocoder.yudao.module.tk.service.generation.route.TkGenerationRoute;
import cn.iocoder.yudao.module.tk.service.generation.route.TkGenerationRouteService;
import cn.iocoder.yudao.module.tk.service.log.TkBusinessLogService;
import cn.iocoder.yudao.module.tk.service.log.TkBusinessTraceIdGenerator;
import cn.iocoder.yudao.module.tk.service.material.TkMaterialLibraryService;
import cn.iocoder.yudao.module.tk.service.reference.TkReferenceAnalysisService;
import cn.iocoder.yudao.module.tk.service.scope.TkDataScopeService;
import cn.iocoder.yudao.module.tk.service.scope.TkUserScope;
import cn.iocoder.yudao.module.tk.service.voice.TkMimoVoiceSelection;
import cn.iocoder.yudao.module.tk.service.voice.TkVoiceProfileService;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.tk.enums.ErrorCodeConstants.*;

@Service
@Validated
public class TkGenerationTaskServiceImpl implements TkGenerationTaskService {

    private static final long MAX_OPENING_VIDEO_SIZE = 100L * 1024 * 1024;
    private static final String[] ALLOWED_EXTENSIONS = {"mp4", "mov", "webm"};
    private static final int DEFAULT_CLIP_SECONDS = 3;
    private static final String DEFAULT_SUBTITLE_STYLE = "classic_white";
    private static final String DEFAULT_SUBTITLE_POSITION_MODE = "smart_safe";
    private static final String DEFAULT_SUBTITLE_KEYWORD_MODE = "auto_manual";
    private static final String DEFAULT_SUBTITLE_ACTIVE_COLOR = "#35F27A";
    private static final String DEFAULT_SUBTITLE_KEYWORD_COLOR = "#FFD84D";
    private static final String DEFAULT_SUBTITLE_FONT_SIZE = "medium";
    private static final String FAIL_CODE_SUBTITLE_FAILED = "SUBTITLE_FAILED";
    private static final String FAIL_REASON_ASR_TEXT_MISMATCH = "ASR_TEXT_MISMATCH";
    private static final String MANUAL_LEAD_GENERATION_SOURCE_PREFIX = "manual-lead-generation://";
    private static final int MAX_VIDEOS_PER_SCRIPT = 5;
    private static final int MAX_BATCH_TASK_COUNT = 30;

    @Resource
    private TkGenerationTaskMapper taskMapper;
    @Resource
    private TkGenerationBatchMapper batchMapper;
    @Resource
    private TkMaterialLibraryService libraryService;
    @Resource
    private TkDataScopeService dataScopeService;
    @Resource
    private FileApi fileApi;
    @Resource
    private TkGenerationPipelineService generationPipelineService;
    @Resource
    private TkGenerationProperties generationProperties;
    @Resource
    private TkReferenceAnalysisService referenceAnalysisService;
    @Resource
    private TkCreditService creditService;
    @Resource
    private TkBusinessLogService businessLogService;
    @Resource
    private TkGenerationPrecheckService precheckService;
    @Resource
    private TkVoiceProfileService voiceProfileService;
    @Resource
    private TkBgmAssetService bgmAssetService;

    @Override
    public Long createGenerationTask(TkGenerationTaskCreateReqVO createReqVO) {
        validateRequestedReferenceDuration(createReqVO);
        return createGenerationTask(createReqVO, null, true);
    }

    @Override
    public List<Long> createGenerationTasks(TkGenerationTaskCreateReqVO createReqVO) {
        validateRequestedReferenceDuration(createReqVO);
        List<Long> scriptOptionIds = normalizeBatchScriptOptionIds(createReqVO);
        int videosPerScript = normalizeVideosPerScript(createReqVO.getVideosPerScript());
        int totalCount = scriptOptionIds.size() * videosPerScript;
        if (totalCount > MAX_BATCH_TASK_COUNT) {
            throw new IllegalArgumentException("batch task count must be <= 30");
        }
        TkMaterialLibraryDO library = libraryService.validateMaterialLibraryReadable(createReqVO.getLibraryId());
        TkUserScope scope = dataScopeService.getCurrentScope();
        Long companyId = resolveCompanyId(createReqVO.getCompanyId(), library, scope);
        Long batchId = createBatch(createReqVO, library, companyId, scriptOptionIds.size(), videosPerScript, totalCount);
        List<Long> ids = new ArrayList<>(totalCount);
        for (int scriptIndex = 0; scriptIndex < scriptOptionIds.size(); scriptIndex++) {
            Long scriptOptionId = scriptOptionIds.get(scriptIndex);
            for (int videoIndex = 0; videoIndex < videosPerScript; videoIndex++) {
                TkGenerationTaskCreateReqVO itemReqVO = copyCreateReqVO(createReqVO);
                itemReqVO.setScriptOptionId(scriptOptionId);
                itemReqVO.setScriptOptionIds(null);
                itemReqVO.setVideosPerScript(1);
                ids.add(createGenerationTask(itemReqVO, null, true, batchId, scriptIndex + 1, videoIndex + 1));
            }
        }
        refreshBatchProgress(batchId);
        return ids;
    }

    @Override
    public Long createGenerationTask(TkGenerationTaskCreateReqVO createReqVO, MultipartFile openingVideoFile) {
        validateRequestedReferenceDuration(createReqVO);
        return createGenerationTask(createReqVO, openingVideoFile, true);
    }

    private void validateRequestedReferenceDuration(TkGenerationTaskCreateReqVO createReqVO) {
        if (createReqVO == null || createReqVO.getReferenceDuration() == null) {
            return;
        }
        int maxDuration = resolveMaxReferenceDuration();
        if (createReqVO.getReferenceDuration() > maxDuration) {
            throw new IllegalArgumentException("目标时长不能超过系统上限 " + maxDuration + " 秒");
        }
    }

    private int resolveMaxReferenceDuration() {
        if (generationProperties == null || generationProperties.getFfmpeg() == null
                || generationProperties.getFfmpeg().getMaxTargetDuration() == null
                || generationProperties.getFfmpeg().getMaxTargetDuration() <= 0) {
            return TkVideoDurationSupport.MAX_TARGET_DURATION;
        }
        return Math.min(TkVideoDurationSupport.MAX_TARGET_DURATION,
                generationProperties.getFfmpeg().getMaxTargetDuration());
    }

    private List<Long> normalizeBatchScriptOptionIds(TkGenerationTaskCreateReqVO createReqVO) {
        List<Long> ids = createReqVO.getScriptOptionIds() == null ? new ArrayList<>() : createReqVO.getScriptOptionIds().stream()
                .filter(item -> item != null && item > 0)
                .distinct()
                .collect(Collectors.toList());
        if (ids.isEmpty() && createReqVO.getScriptOptionId() != null) {
            ids.add(createReqVO.getScriptOptionId());
        }
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("scriptOptionIds must not be empty");
        }
        return ids;
    }

    private int normalizeVideosPerScript(Integer videosPerScript) {
        int count = videosPerScript == null ? 1 : videosPerScript;
        if (count < 1 || count > MAX_VIDEOS_PER_SCRIPT) {
            throw new IllegalArgumentException("videosPerScript must be between 1 and 5");
        }
        return count;
    }

    private TkGenerationTaskCreateReqVO copyCreateReqVO(TkGenerationTaskCreateReqVO source) {
        TkGenerationTaskCreateReqVO target = new TkGenerationTaskCreateReqVO();
        target.setCompanyId(source.getCompanyId());
        target.setSourceUrl(source.getSourceUrl());
        target.setLibraryId(source.getLibraryId());
        target.setProductId(source.getProductId());
        target.setTemplateId(source.getTemplateId());
        target.setVoiceId(source.getVoiceId());
        target.setTtsProvider(source.getTtsProvider());
        target.setVoiceCode(source.getVoiceCode());
        target.setVoiceProfileId(source.getVoiceProfileId());
        target.setVoiceEnabled(source.getVoiceEnabled());
        target.setMimoVoiceMode(source.getMimoVoiceMode());
        target.setMimoVoiceCode(source.getMimoVoiceCode());
        target.setMimoVoicePrompt(source.getMimoVoicePrompt());
        target.setMimoVoiceSampleUrl(source.getMimoVoiceSampleUrl());
        target.setTargetLanguage(source.getTargetLanguage());
        target.setMaterialPurpose(source.getMaterialPurpose());
        target.setProductCategoryCode(source.getProductCategoryCode());
        target.setClipPlanMode(source.getClipPlanMode());
        target.setReferenceAnalysisId(source.getReferenceAnalysisId());
        target.setScriptOptionId(source.getScriptOptionId());
        target.setOpeningVideoUrl(source.getOpeningVideoUrl());
        target.setOpeningVideoName(source.getOpeningVideoName());
        target.setOpeningProcessMode(source.getOpeningProcessMode());
        target.setOpeningClipStartSecond(source.getOpeningClipStartSecond());
        target.setOpeningClipEndSecond(source.getOpeningClipEndSecond());
        target.setReferenceDuration(source.getReferenceDuration());
        target.setSegmentDurationConfig(source.getSegmentDurationConfig());
        target.setPromptText(source.getPromptText());
        target.setBgmEnabled(source.getBgmEnabled());
        target.setBgmAssetId(source.getBgmAssetId());
        target.setBgmVolume(source.getBgmVolume());
        target.setSubtitleEnabled(source.getSubtitleEnabled());
        target.setSubtitleStyle(source.getSubtitleStyle());
        target.setSubtitlePositionMode(source.getSubtitlePositionMode());
        target.setSubtitleKeywordEnabled(source.getSubtitleKeywordEnabled());
        target.setSubtitleKeywords(source.getSubtitleKeywords());
        target.setSubtitleKeywordMode(source.getSubtitleKeywordMode());
        target.setSubtitleKaraokeEnabled(source.getSubtitleKaraokeEnabled());
        target.setSubtitleActiveColor(source.getSubtitleActiveColor());
        target.setSubtitleKeywordColor(source.getSubtitleKeywordColor());
        target.setSubtitleFontSize(source.getSubtitleFontSize());
        return target;
    }

    private Long createGenerationTask(TkGenerationTaskCreateReqVO createReqVO, MultipartFile openingVideoFile, boolean startPipeline) {
        return createGenerationTask(createReqVO, openingVideoFile, startPipeline, null, null, null);
    }

    private Long createGenerationTask(TkGenerationTaskCreateReqVO createReqVO, MultipartFile openingVideoFile,
                                      boolean startPipeline, Long batchId, Integer scriptIndex, Integer videoIndex) {
        if (openingVideoFile != null && StrUtil.isBlank(createReqVO.getOpeningVideoName())) {
            createReqVO.setOpeningVideoName(StrUtil.blankToDefault(openingVideoFile.getOriginalFilename(), "opening.mp4"));
        }
        if (openingVideoFile == null && StrUtil.isBlank(createReqVO.getOpeningVideoUrl())) {
            createReqVO.setOpeningVideoName(null);
            createReqVO.setOpeningProcessMode(null);
            createReqVO.setOpeningClipStartSecond(null);
            createReqVO.setOpeningClipEndSecond(null);
        }
        TkMaterialLibraryDO library = libraryService.validateMaterialLibraryReadable(createReqVO.getLibraryId());
        TkUserScope scope = dataScopeService.getCurrentScope();
        Long companyId = resolveCompanyId(createReqVO.getCompanyId(), library, scope);
        Long[] result = new Long[1];
        TenantUtils.execute(library.getTenantId(),
                () -> result[0] = createGenerationTaskWithinTenant(createReqVO, openingVideoFile, startPipeline,
                        library, companyId, batchId, scriptIndex, videoIndex));
        return result[0];
    }

    private Long createGenerationTaskWithinTenant(TkGenerationTaskCreateReqVO createReqVO, MultipartFile openingVideoFile,
                                                  boolean startPipeline, TkMaterialLibraryDO library, Long companyId,
                                                  Long batchId, Integer scriptIndex, Integer videoIndex) {
        Long tenantId = library.getTenantId();
        String businessTraceId = TkBusinessTraceIdGenerator.generate(tenantId);
        TkGenerationPrecheckRespVO precheck = precheckService.precheck(createReqVO);
        if (!Boolean.TRUE.equals(precheck.getPassed())) {
            throw new IllegalStateException(precheck.getErrors().isEmpty()
                    ? "生成预检失败" : precheck.getErrors().get(0).getMessage());
        }
        Long creditLogId = creditService.freezeForGenerationTask(tenantId);
        try {
            OpeningVideo openingVideo = uploadOpeningVideoIfPresent(openingVideoFile, tenantId, companyId);
            if (openingVideo == null && StrUtil.isNotBlank(createReqVO.getOpeningVideoUrl())) {
                openingVideo = createOpeningVideoFromLink(createReqVO);
            }
            String targetLanguage = TkLanguageSupport.normalize(createReqVO.getTargetLanguage());
            String materialPurpose = TkGeminiPromptConfig.normalizeMaterialPurpose(createReqVO.getMaterialPurpose());
            String promptText = resolvePromptText(createReqVO, materialPurpose);
            String sourceUrl = resolveSourceUrl(createReqVO, materialPurpose);
            if (createReqVO.getReferenceAnalysisId() != null) {
                TkReferenceAnalysisDO analysis = referenceAnalysisService.validateAnalysisReadable(createReqVO.getReferenceAnalysisId());
                if (!createReqVO.getLibraryId().equals(analysis.getLibraryId())) {
                    throw exception(TK_REFERENCE_BINDING_MISMATCH);
                }
                if (StrUtil.isNotBlank(analysis.getBusinessTraceId())) {
                    businessTraceId = analysis.getBusinessTraceId();
                }
                if (StrUtil.isNotBlank(analysis.getTargetLanguage())) {
                    targetLanguage = TkLanguageSupport.normalize(analysis.getTargetLanguage());
                }
                materialPurpose = TkGeminiPromptConfig.normalizeMaterialPurpose(analysis.getMaterialPurpose());
                promptText = resolvePromptText(createReqVO, materialPurpose);
            }
            boolean voiceEnabled = resolveVoiceEnabled(createReqVO, materialPurpose, promptText);
            String ttsProvider = resolveTtsProvider(createReqVO);
            String resolvedVoiceCode = voiceEnabled && TkTtsProviderEnum.DASHSCOPE.equals(ttsProvider)
                    ? voiceProfileService.resolveVoiceSelection(createReqVO.getVoiceProfileId(), createReqVO.getVoiceCode())
                    : null;
            TkMimoVoiceSelection mimoVoiceSelection = voiceEnabled && TkTtsProviderEnum.MIMO.equals(ttsProvider)
                    ? resolveMimoVoiceSelection(createReqVO)
                    : null;
            boolean subtitleEnabled = voiceEnabled && (createReqVO.getSubtitleEnabled() == null || createReqVO.getSubtitleEnabled());
            if (createReqVO.getScriptOptionId() != null) {
                TkReferenceScriptOptionDO option = referenceAnalysisService.validateScriptOptionReadable(createReqVO.getScriptOptionId());
                if (!createReqVO.getLibraryId().equals(option.getLibraryId())) {
                    throw exception(TK_REFERENCE_BINDING_MISMATCH);
                }
                if (createReqVO.getReferenceAnalysisId() != null && !createReqVO.getReferenceAnalysisId().equals(option.getAnalysisId())) {
                    throw exception(TK_REFERENCE_BINDING_MISMATCH);
                }
            }
            int referenceDuration = TkVideoDurationSupport.normalize(createReqVO.getReferenceDuration(),
                    generationProperties.getFfmpeg().getMaxTargetDuration());
            TkGenerationRoute generationRoute = resolveGenerationRoute(tenantId, materialPurpose,
                    createReqVO.getProductCategoryCode());
            String generationRouteConfig = resolveGenerationRouteConfig(createReqVO, materialPurpose, generationRoute);
            BgmSelection bgmSelection = resolveBgmSelection(createReqVO, materialPurpose);
            String openingProcessMode = openingVideo == null ? null
                    : TkNativeOpeningSupport.normalizeMode(createReqVO.getOpeningProcessMode());
            TkGenerationTaskDO task = TkGenerationTaskDO.builder()
                    .businessTraceId(businessTraceId)
                    .batchId(batchId)
                    .scriptIndex(scriptIndex)
                    .videoIndex(videoIndex)
                    .companyId(companyId)
                    .sourceUrl(sourceUrl)
                    .libraryId(createReqVO.getLibraryId())
                    .productId(createReqVO.getProductId())
                    .templateId(createReqVO.getTemplateId())
                    .voiceId(createReqVO.getVoiceId())
                    .ttsProvider(ttsProvider)
                    .voiceCode(resolvedVoiceCode)
                    .voiceProfileId(createReqVO.getVoiceProfileId())
                    .voiceEnabled(voiceEnabled)
                    .mimoVoiceMode(mimoVoiceSelection == null ? null : mimoVoiceSelection.getMode())
                    .mimoVoiceCode(mimoVoiceSelection == null ? null : mimoVoiceSelection.getCode())
                    .mimoVoicePrompt(mimoVoiceSelection == null ? null : mimoVoiceSelection.getPrompt())
                    .mimoVoiceSampleUrl(mimoVoiceSelection == null ? null : mimoVoiceSelection.getSampleUrl())
                    .targetLanguage(targetLanguage)
                    .materialPurpose(materialPurpose)
                    .productCategoryCode(generationRoute.getProductCategoryCode())
                    .generationRouteCode(generationRoute.getRouteCode())
                    .generationRouteConfig(generationRouteConfig)
                    .referenceAnalysisId(createReqVO.getReferenceAnalysisId())
                    .scriptOptionId(createReqVO.getScriptOptionId())
                    .openingVideoUrl(openingVideo == null ? null : openingVideo.url)
                    .openingVideoName(openingVideo == null ? null : openingVideo.name)
                    .openingProcessMode(openingProcessMode)
                    .openingClipStartSecond(openingVideo == null ? null : openingVideo.startSecond)
                    .openingClipEndSecond(openingVideo == null ? null : openingVideo.endSecond)
                    .referenceDuration(referenceDuration)
                    .segmentDurationConfig(createReqVO.getSegmentDurationConfig())
                    .clipSeconds(generationProperties.getFfmpeg().getClipSeconds() == null
                            ? DEFAULT_CLIP_SECONDS : generationProperties.getFfmpeg().getClipSeconds())
                    .promptText(promptText)
                    .bgmEnabled(bgmSelection.enabled)
                    .bgmAssetId(bgmSelection.assetId)
                    .bgmSourceType(bgmSelection.sourceType)
                    .bgmUrl(bgmSelection.url)
                    .bgmVolume(bgmSelection.volume)
                    .subtitleEnabled(subtitleEnabled)
                    .subtitleStyle(StrUtil.blankToDefault(createReqVO.getSubtitleStyle(), DEFAULT_SUBTITLE_STYLE))
                    .subtitlePositionMode(StrUtil.blankToDefault(createReqVO.getSubtitlePositionMode(), DEFAULT_SUBTITLE_POSITION_MODE))
                    .subtitleKeywordEnabled(Boolean.TRUE.equals(createReqVO.getSubtitleKeywordEnabled()))
                    .subtitleKeywords(createReqVO.getSubtitleKeywords())
                    .subtitleKeywordMode(StrUtil.blankToDefault(createReqVO.getSubtitleKeywordMode(), DEFAULT_SUBTITLE_KEYWORD_MODE))
                    .subtitleKaraokeEnabled(Boolean.TRUE.equals(createReqVO.getSubtitleKaraokeEnabled()))
                    .subtitleActiveColor(StrUtil.blankToDefault(createReqVO.getSubtitleActiveColor(), DEFAULT_SUBTITLE_ACTIVE_COLOR))
                    .subtitleKeywordColor(StrUtil.blankToDefault(createReqVO.getSubtitleKeywordColor(), DEFAULT_SUBTITLE_KEYWORD_COLOR))
                    .subtitleFontSize(StrUtil.blankToDefault(createReqVO.getSubtitleFontSize(), DEFAULT_SUBTITLE_FONT_SIZE))
                    .status(TkGenerationStatusEnum.PENDING)
                    .progress(0)
                    .currentStep("PENDING")
                    .precheckResult(JsonUtils.toJsonString(precheck))
                    .retryCount(0)
                    .title(StrUtil.format("{} · 智能混剪任务", library.getName()))
                    .build();
            task.setTenantId(tenantId);
            taskMapper.insert(task);
            creditService.bindBusiness(creditLogId, task.getId());
            businessLogService.info(businessTraceId, "GENERATION_TASK", task.getId(), "CREATE", task.getStatus(),
                    StrUtil.format("创建生成任务：{}", task.getTitle()), task);
            if (startPipeline) {
                generationPipelineService.submit(tenantId, task.getId());
            }
            return task.getId();
        } catch (Exception ex) {
            creditService.refundByLogId(creditLogId, ex.getMessage());
            businessLogService.error(businessTraceId, "GENERATION_TASK", null, "CREATE_FAILED", "FAILED",
                    StrUtil.format("创建生成任务失败：{}", ex.getMessage()), createReqVO);
            throw ex;
        }
    }

    private Long resolveCompanyId(Long requestedCompanyId, TkMaterialLibraryDO library, TkUserScope scope) {
        if (requestedCompanyId == null) {
            return library.getCompanyId();
        }
        return dataScopeService.getWritableCompanyId(requestedCompanyId);
    }

    private TkGenerationRoute resolveGenerationRoute(Long tenantId, String materialPurpose, String productCategoryCode) {
        String routeCode = TkGeminiPromptConfig.isLeadGeneration(materialPurpose)
                ? TkGenerationRouteService.DEFAULT_LEAD_GENERATION_ROUTE_CODE
                : TkGenerationRouteService.DEFAULT_ECOMMERCE_ROUTE_CODE;
        return new TkGenerationRoute(TkGenerationRouteService.DEFAULT_PRODUCT_CATEGORY_CODE, routeCode, null);
    }

    private String resolveGenerationRouteConfig(TkGenerationTaskCreateReqVO createReqVO, String materialPurpose,
                                                TkGenerationRoute generationRoute) {
        if (StrUtil.isNotBlank(createReqVO.getClipPlanMode())) {
            return TkGenerationRouteConfigSupport.buildClipPlanModeConfig(createReqVO.getClipPlanMode());
        }
        return generationRoute == null ? null : generationRoute.getRouteConfig();
    }

    private String resolveSourceUrl(TkGenerationTaskCreateReqVO createReqVO, String materialPurpose) {
        String sourceUrl = StrUtil.trimToEmpty(createReqVO.getSourceUrl());
        if (StrUtil.isNotBlank(sourceUrl)) {
            return sourceUrl;
        }
        if (!TkGeminiPromptConfig.isLeadGeneration(materialPurpose)) {
            throw new IllegalArgumentException("TikTok 对标链接不能为空");
        }
        return MANUAL_LEAD_GENERATION_SOURCE_PREFIX + createReqVO.getLibraryId();
    }

    private String resolvePromptText(TkGenerationTaskCreateReqVO createReqVO, String materialPurpose) {
        if (TkGeminiPromptConfig.isLeadGeneration(materialPurpose)) {
            return StrUtil.trimToEmpty(createReqVO.getPromptText());
        }
        return StrUtil.blankToDefault(createReqVO.getPromptText(), generationProperties.getPrompt());
    }

    private String resolveTtsProvider(TkGenerationTaskCreateReqVO createReqVO) {
        return TkTtsProviderEnum.normalize(createReqVO.getTtsProvider());
    }

    private String resolveMimoVoiceMode(TkGenerationTaskCreateReqVO createReqVO) {
        return TkMimoVoiceModeEnum.normalize(createReqVO.getMimoVoiceMode());
    }

    private TkMimoVoiceSelection resolveMimoVoiceSelection(TkGenerationTaskCreateReqVO createReqVO) {
        TkMimoVoiceSelection selection = voiceProfileService.resolveMimoVoiceSelection(
                createReqVO.getVoiceProfileId(), createReqVO.getMimoVoiceMode(), createReqVO.getMimoVoiceCode(),
                createReqVO.getMimoVoicePrompt(), createReqVO.getMimoVoiceSampleUrl());
        String mode = selection.getMode();
        String code = resolveMimoVoiceCode(createReqVO, mode, selection.getCode());
        String prompt = resolveMimoVoicePrompt(createReqVO, mode, selection.getPrompt());
        String sampleUrl = resolveMimoVoiceSampleUrl(createReqVO, mode, selection.getSampleUrl());
        return new TkMimoVoiceSelection(mode, code, prompt, sampleUrl);
    }

    private String resolveMimoVoiceCode(TkGenerationTaskCreateReqVO createReqVO, String mimoVoiceMode) {
        if (!TkMimoVoiceModeEnum.PRESET.equals(mimoVoiceMode)) {
            return null;
        }
        if (StrUtil.isBlank(createReqVO.getMimoVoiceCode())) {
            throw new IllegalArgumentException("MiMo 预置音色不能为空");
        }
        return StrUtil.trimToEmpty(createReqVO.getMimoVoiceCode());
    }

    private String resolveMimoVoicePrompt(TkGenerationTaskCreateReqVO createReqVO, String mimoVoiceMode) {
        if (!TkMimoVoiceModeEnum.VOICE_DESIGN.equals(mimoVoiceMode)) {
            return StrUtil.trimToEmpty(createReqVO.getMimoVoicePrompt());
        }
        if (StrUtil.isBlank(createReqVO.getMimoVoicePrompt())) {
            throw new IllegalArgumentException("MiMo 音色设计描述不能为空");
        }
        return StrUtil.trimToEmpty(createReqVO.getMimoVoicePrompt());
    }

    private String resolveMimoVoiceSampleUrl(TkGenerationTaskCreateReqVO createReqVO, String mimoVoiceMode) {
        if (!TkMimoVoiceModeEnum.VOICE_CLONE.equals(mimoVoiceMode)) {
            return StrUtil.trimToEmpty(createReqVO.getMimoVoiceSampleUrl());
        }
        if (StrUtil.isBlank(createReqVO.getMimoVoiceSampleUrl())) {
            throw new IllegalArgumentException("MiMo 音色复刻样本不能为空");
        }
        return StrUtil.trimToEmpty(createReqVO.getMimoVoiceSampleUrl());
    }

    private String resolveMimoVoiceCode(TkGenerationTaskCreateReqVO createReqVO, String mimoVoiceMode, String mimoVoiceCode) {
        if (!TkMimoVoiceModeEnum.PRESET.equals(mimoVoiceMode)) {
            return null;
        }
        if (StrUtil.isBlank(mimoVoiceCode)) {
            throw new IllegalArgumentException("MiMo preset voice must not be empty");
        }
        return StrUtil.trimToEmpty(mimoVoiceCode);
    }

    private String resolveMimoVoicePrompt(TkGenerationTaskCreateReqVO createReqVO, String mimoVoiceMode, String prompt) {
        if (!TkMimoVoiceModeEnum.VOICE_DESIGN.equals(mimoVoiceMode)) {
            return StrUtil.trimToEmpty(prompt);
        }
        if (StrUtil.isBlank(prompt)) {
            throw new IllegalArgumentException("MiMo voice design prompt must not be empty");
        }
        return StrUtil.trimToEmpty(prompt);
    }

    private String resolveMimoVoiceSampleUrl(TkGenerationTaskCreateReqVO createReqVO, String mimoVoiceMode, String sampleUrl) {
        if (!TkMimoVoiceModeEnum.VOICE_CLONE.equals(mimoVoiceMode)) {
            return StrUtil.trimToEmpty(sampleUrl);
        }
        if (StrUtil.isBlank(sampleUrl)) {
            throw new IllegalArgumentException("MiMo voice clone sample URL must not be empty");
        }
        return StrUtil.trimToEmpty(sampleUrl);
    }

    private boolean resolveVoiceEnabled(TkGenerationTaskCreateReqVO createReqVO, String materialPurpose, String promptText) {
        if (!TkGeminiPromptConfig.isLeadGeneration(materialPurpose)) {
            return true;
        }
        if (StrUtil.isBlank(promptText)) {
            return false;
        }
        return createReqVO.getVoiceEnabled() == null || createReqVO.getVoiceEnabled();
    }

    private BgmSelection resolveBgmSelection(TkGenerationTaskCreateReqVO createReqVO, String materialPurpose) {
        if (!TkGeminiPromptConfig.isLeadGeneration(materialPurpose)
                || !Boolean.TRUE.equals(createReqVO.getBgmEnabled())
                || createReqVO.getBgmAssetId() == null) {
            return BgmSelection.disabled();
        }
        TkBgmAssetDO asset = bgmAssetService.validateReadable(createReqVO.getBgmAssetId());
        return new BgmSelection(true, asset.getId(), asset.getSourceType(), asset.getFileUrl(),
                normalizeBgmVolume(createReqVO.getBgmVolume()));
    }

    private double normalizeBgmVolume(Double bgmVolume) {
        if (bgmVolume == null) {
            return 0.10D;
        }
        return Math.max(0.01D, Math.min(0.30D, bgmVolume));
    }

    private OpeningVideo uploadOpeningVideoIfPresent(MultipartFile file, Long tenantId, Long companyId) {
        if (file == null) {
            return null;
        }
        if (file.isEmpty()) {
            throw exception(TK_UPLOAD_FILE_EMPTY);
        }
        if (file.getSize() > MAX_OPENING_VIDEO_SIZE) {
            throw exception(TK_UPLOAD_FILE_TOO_LARGE);
        }
        String originalFilename = file.getOriginalFilename() == null ? "opening.mp4" : file.getOriginalFilename();
        String extension = StrUtil.blankToDefault(FileUtil.extName(originalFilename), "").toLowerCase(Locale.ROOT);
        if (!Arrays.asList(ALLOWED_EXTENSIONS).contains(extension)) {
            throw exception(TK_UPLOAD_FILE_EXTENSION_INVALID);
        }
        try {
            String directory = StrUtil.format("tk/{}/{}/generation-openings", tenantId, companyId);
            return new OpeningVideo(fileApi.createFile(file.getBytes(), originalFilename, directory, file.getContentType()), originalFilename);
        } catch (IOException ex) {
            throw exception(TK_UPLOAD_FILE_EMPTY);
        }
    }

    private OpeningVideo createOpeningVideoFromLink(TkGenerationTaskCreateReqVO createReqVO) {
        return new OpeningVideo(createReqVO.getOpeningVideoUrl(),
                StrUtil.blankToDefault(createReqVO.getOpeningVideoName(), "opening.mp4"));
    }

    private static class OpeningVideo {

        private final String url;
        private final String name;
        private final Integer startSecond;
        private final Integer endSecond;

        private OpeningVideo(String url, String name) {
            this(url, name, null, null);
        }

        private OpeningVideo(String url, String name, Integer startSecond, Integer endSecond) {
            this.url = url;
            this.name = name;
            this.startSecond = startSecond;
            this.endSecond = endSecond;
        }

    }

    private static class BgmSelection {

        private final boolean enabled;
        private final Long assetId;
        private final String sourceType;
        private final String url;
        private final Double volume;

        private BgmSelection(boolean enabled, Long assetId, String sourceType, String url, Double volume) {
            this.enabled = enabled;
            this.assetId = assetId;
            this.sourceType = sourceType;
            this.url = url;
            this.volume = volume;
        }

        private static BgmSelection disabled() {
            return new BgmSelection(false, null, null, null, null);
        }

    }

    @Override
    public void retryGenerationTask(Long id) {
        TkGenerationTaskDO task = getGenerationTask(id);
        if (!TkGenerationStatusEnum.FAILED.equals(task.getStatus())) {
            throw exception(TK_GENERATION_RETRY_STATUS_INVALID);
        }
        boolean clearAudio = shouldRegenerateAudioOnRetry(task);
        taskMapper.resetForRetry(id, (task.getRetryCount() == null ? 0 : task.getRetryCount()) + 1,
                java.time.LocalDateTime.now(), clearAudio);
        generationPipelineService.submit(task.getTenantId(), task.getId());
        refreshBatchProgress(task.getBatchId());
    }

    private boolean shouldRegenerateAudioOnRetry(TkGenerationTaskDO task) {
        return task != null
                && FAIL_CODE_SUBTITLE_FAILED.equals(task.getFailCode())
                && StrUtil.containsIgnoreCase(task.getFailReason(), FAIL_REASON_ASR_TEXT_MISMATCH);
    }

    @Override
    public PageResult<TkGenerationTaskDO> getGenerationTaskPage(TkGenerationTaskPageReqVO pageReqVO) {
        return taskMapper.selectPage(pageReqVO, dataScopeService.getCurrentScope());
    }

    @Override
    public PageResult<TkGenerationTaskDO> getGenerationTaskSummaryPage(TkGenerationTaskPageReqVO pageReqVO) {
        return taskMapper.selectSummaryPage(pageReqVO, dataScopeService.getCurrentScope());
    }

    @Override
    public Map<Long, Integer> getDailyUserVideoNoMap(Collection<TkGenerationTaskDO> tasks) {
        Map<Long, Integer> result = new HashMap<>();
        if (tasks == null || tasks.isEmpty()) {
            return result;
        }
        List<TkGenerationTaskDO> validTasks = tasks.stream()
                .filter(task -> task.getId() != null && task.getCreateTime() != null && StrUtil.isNotBlank(task.getCreator()))
                .collect(Collectors.toList());
        LocalDate minDate = validTasks.stream().map(task -> task.getCreateTime().toLocalDate())
                .min(LocalDate::compareTo).orElse(LocalDate.now());
        LocalDate maxDate = validTasks.stream().map(task -> task.getCreateTime().toLocalDate())
                .max(LocalDate::compareTo).orElse(minDate);
        Set<Long> tenantIds = validTasks.stream().map(TkGenerationTaskDO::getTenantId).collect(Collectors.toSet());
        Set<String> creators = validTasks.stream().map(TkGenerationTaskDO::getCreator).collect(Collectors.toSet());
        Set<Long> pageTaskIds = validTasks.stream().map(TkGenerationTaskDO::getId).collect(Collectors.toSet());
        Map<String, List<TkGenerationTaskDO>> groupMap = taskMapper.selectDailySequenceCandidates(
                        tenantIds, creators, minDate.atStartOfDay(), maxDate.plusDays(1).atStartOfDay()).stream()
                .collect(Collectors.groupingBy(this::buildDailySequenceGroupKey));
        groupMap.values().forEach(groupTasks -> {
            for (int i = 0; i < groupTasks.size(); i++) {
                Long taskId = groupTasks.get(i).getId();
                if (pageTaskIds.contains(taskId)) {
                    result.put(taskId, i + 1);
                }
            }
        });
        return result;
    }

    private String buildDailySequenceGroupKey(TkGenerationTaskDO task) {
        return task.getTenantId() + "|" + task.getCreator() + "|" + task.getCreateTime().toLocalDate();
    }

    @Override
    public List<TkGenerationTaskDO> getGenerationTaskStatusBatch(Collection<Long> ids) {
        return taskMapper.selectStatusBatch(ids, dataScopeService.getCurrentScope());
    }

    @Override
    public TkGenerationTaskDO getGenerationTask(Long id) {
        TkGenerationTaskDO task = taskMapper.selectById(id);
        if (task == null) {
            throw exception(TK_GENERATION_TASK_NOT_EXISTS);
        }
        dataScopeService.validateReadable(task.getTenantId(), task.getCompanyId(), task.getCreator());
        return task;
    }

    private Long createBatch(TkGenerationTaskCreateReqVO createReqVO, TkMaterialLibraryDO library, Long companyId,
                             int scriptCount, int videosPerScript, int totalCount) {
        Long[] result = new Long[1];
        TenantUtils.execute(library.getTenantId(), () -> {
            TkGenerationBatchDO batch = TkGenerationBatchDO.builder()
                    .batchNo("B" + System.currentTimeMillis())
                    .name(StrUtil.format("{} · {} scripts x {}", library.getName(), scriptCount, videosPerScript))
                    .companyId(companyId)
                    .libraryId(library.getId())
                    .sourceUrl(createReqVO.getSourceUrl())
                    .targetLanguage(TkLanguageSupport.normalize(createReqVO.getTargetLanguage()))
                    .scriptCount(scriptCount)
                    .videosPerScript(videosPerScript)
                    .expectedVideoCount(totalCount)
                    .createdTaskCount(0)
                    .successTaskCount(0)
                    .failedTaskCount(0)
                    .runningTaskCount(0)
                    .progressPercent(0)
                    .status("PENDING")
                    .build();
            batch.setTenantId(library.getTenantId());
            batchMapper.insert(batch);
            result[0] = batch.getId();
        });
        return result[0];
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
                .map(task -> StrUtil.blankToDefault(task.getFailCode(), task.getFailReason()))
                .filter(StrUtil::isNotBlank)
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

}

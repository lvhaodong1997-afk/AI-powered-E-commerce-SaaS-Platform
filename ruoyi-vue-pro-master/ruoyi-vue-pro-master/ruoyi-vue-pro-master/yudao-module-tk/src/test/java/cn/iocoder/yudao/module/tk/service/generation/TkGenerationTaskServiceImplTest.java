package cn.iocoder.yudao.module.tk.service.generation;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.tk.controller.admin.generation.vo.TkGenerationTaskPageReqVO;
import cn.iocoder.yudao.module.tk.controller.admin.generation.vo.TkGenerationPrecheckRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.generation.vo.TkGenerationTaskCreateReqVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkBgmAssetDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationBatchDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationTaskDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkMaterialLibraryDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkReferenceAnalysisDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkGenerationBatchMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.TkGenerationTaskMapper;
import cn.iocoder.yudao.module.tk.enums.TkGenerationStatusEnum;
import cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties;
import cn.iocoder.yudao.module.tk.service.bgm.TkBgmAssetService;
import cn.iocoder.yudao.module.tk.service.credit.TkCreditService;
import cn.iocoder.yudao.module.tk.service.generation.pipeline.TkGenerationPipelineService;
import cn.iocoder.yudao.module.tk.service.generation.pipeline.TkGeminiPromptConfig;
import cn.iocoder.yudao.module.tk.service.generation.route.TkGenerationRouteService;
import cn.iocoder.yudao.module.tk.service.log.TkBusinessLogService;
import cn.iocoder.yudao.module.tk.service.material.TkMaterialLibraryService;
import cn.iocoder.yudao.module.tk.service.reference.TkReferenceAnalysisService;
import cn.iocoder.yudao.module.tk.service.scope.TkDataScopeService;
import cn.iocoder.yudao.module.tk.service.scope.TkUserScope;
import cn.iocoder.yudao.module.tk.service.voice.TkMimoVoiceSelection;
import cn.iocoder.yudao.module.tk.service.voice.TkVoiceProfileService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TkGenerationTaskServiceImplTest {

    @Test
    void createGenerationTaskRejectsReferenceDurationAboveSystemLimit() {
        TkGenerationTaskServiceImpl service = new TkGenerationTaskServiceImpl();
        TkCreditService creditService = mock(TkCreditService.class);
        ReflectionTestUtils.setField(service, "generationProperties", new TkGenerationProperties());
        ReflectionTestUtils.setField(service, "creditService", creditService);
        TkGenerationTaskCreateReqVO reqVO = new TkGenerationTaskCreateReqVO();
        reqVO.setReferenceDuration(501);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.createGenerationTask(reqVO));

        assertTrue(exception.getMessage().contains("500"));
        verify(creditService, never()).freezeForGenerationTask(any());
    }

    @Test
    void retryGenerationTaskResetsFailedTaskAndSubmitsPipeline() {
        TkGenerationTaskServiceImpl service = new TkGenerationTaskServiceImpl();
        TkGenerationTaskMapper taskMapper = mock(TkGenerationTaskMapper.class);
        TkGenerationPipelineService pipelineService = mock(TkGenerationPipelineService.class);
        TkDataScopeService dataScopeService = mock(TkDataScopeService.class);
        ReflectionTestUtils.setField(service, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(service, "generationPipelineService", pipelineService);
        ReflectionTestUtils.setField(service, "dataScopeService", dataScopeService);

        TkGenerationTaskDO task = TkGenerationTaskDO.builder()
                .id(100L)
                .companyId(20L)
                .status(TkGenerationStatusEnum.FAILED)
                .progress(100)
                .failReason("render failed")
                .build();
        task.setTenantId(10L);
        task.setCreator("1");
        when(taskMapper.selectById(100L)).thenReturn(task);

        service.retryGenerationTask(100L);

        verify(taskMapper).resetForRetry(eq(100L), eq(1), any(), eq(false));
        verify(pipelineService).submit(10L, 100L);
    }

    @Test
    void retryGenerationTaskClearsAudioWhenSubtitleMismatchFailed() {
        TkGenerationTaskServiceImpl service = new TkGenerationTaskServiceImpl();
        TkGenerationTaskMapper taskMapper = mock(TkGenerationTaskMapper.class);
        TkGenerationPipelineService pipelineService = mock(TkGenerationPipelineService.class);
        TkDataScopeService dataScopeService = mock(TkDataScopeService.class);
        ReflectionTestUtils.setField(service, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(service, "generationPipelineService", pipelineService);
        ReflectionTestUtils.setField(service, "dataScopeService", dataScopeService);

        TkGenerationTaskDO task = TkGenerationTaskDO.builder()
                .id(101L)
                .companyId(20L)
                .status(TkGenerationStatusEnum.FAILED)
                .progress(100)
                .failCode("SUBTITLE_FAILED")
                .failReason("视频渲染失败：ASR_TEXT_MISMATCH: audio narration text does not match the original script")
                .audioUrl("https://example.com/old.wav")
                .clipPlan("[]")
                .retryCount(2)
                .build();
        task.setTenantId(10L);
        task.setCreator("1");
        when(taskMapper.selectById(101L)).thenReturn(task);

        service.retryGenerationTask(101L);

        verify(taskMapper).resetForRetry(eq(101L), eq(3), any(), eq(true));
        verify(pipelineService).submit(10L, 101L);
    }

    @Test
    void getGenerationTaskSummaryPageUsesSummaryMapperWithCurrentScope() {
        TkGenerationTaskServiceImpl service = new TkGenerationTaskServiceImpl();
        TkGenerationTaskMapper taskMapper = mock(TkGenerationTaskMapper.class);
        TkDataScopeService dataScopeService = mock(TkDataScopeService.class);
        ReflectionTestUtils.setField(service, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(service, "dataScopeService", dataScopeService);
        TkUserScope scope = new TkUserScope(7L, 10L, "COMPANY_USER", 20L);
        TkGenerationTaskPageReqVO reqVO = new TkGenerationTaskPageReqVO();
        reqVO.setStatus(TkGenerationStatusEnum.RENDERING);
        PageResult<TkGenerationTaskDO> expected = new PageResult<>(java.util.Collections.emptyList(), 0L);
        when(dataScopeService.getCurrentScope()).thenReturn(scope);
        when(taskMapper.selectSummaryPage(reqVO, scope)).thenReturn(expected);

        PageResult<TkGenerationTaskDO> actual = service.getGenerationTaskSummaryPage(reqVO);

        assertEquals(expected, actual);
        verify(taskMapper).selectSummaryPage(reqVO, scope);
    }

    @Test
    void getGenerationTaskStatusBatchUsesStatusMapperWithCurrentScope() {
        TkGenerationTaskServiceImpl service = new TkGenerationTaskServiceImpl();
        TkGenerationTaskMapper taskMapper = mock(TkGenerationTaskMapper.class);
        TkDataScopeService dataScopeService = mock(TkDataScopeService.class);
        ReflectionTestUtils.setField(service, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(service, "dataScopeService", dataScopeService);
        TkUserScope scope = new TkUserScope(7L, 10L, "COMPANY_USER", 20L);
        List<Long> ids = Arrays.asList(100L, 101L);
        List<TkGenerationTaskDO> expected = java.util.Collections.singletonList(
                TkGenerationTaskDO.builder().id(100L).status(TkGenerationStatusEnum.RENDERING).progress(70).build());
        when(dataScopeService.getCurrentScope()).thenReturn(scope);
        when(taskMapper.selectStatusBatch(ids, scope)).thenReturn(expected);

        List<TkGenerationTaskDO> actual = service.getGenerationTaskStatusBatch(ids);

        assertEquals(expected, actual);
        verify(taskMapper).selectStatusBatch(ids, scope);
    }

    @Test
    void createGenerationTaskInheritsBusinessTraceIdFromReferenceAnalysis() {
        TkGenerationTaskServiceImpl service = new TkGenerationTaskServiceImpl();
        TkGenerationTaskMapper taskMapper = mock(TkGenerationTaskMapper.class);
        TkGenerationBatchMapper batchMapper = mock(TkGenerationBatchMapper.class);
        TkMaterialLibraryService libraryService = mock(TkMaterialLibraryService.class);
        TkDataScopeService dataScopeService = mock(TkDataScopeService.class);
        TkGenerationPipelineService pipelineService = mock(TkGenerationPipelineService.class);
        TkReferenceAnalysisService referenceAnalysisService = mock(TkReferenceAnalysisService.class);
        TkCreditService creditService = mock(TkCreditService.class);
        TkBusinessLogService businessLogService = mock(TkBusinessLogService.class);
        TkGenerationPrecheckService precheckService = mock(TkGenerationPrecheckService.class);
        TkVoiceProfileService voiceProfileService = mock(TkVoiceProfileService.class);
        ReflectionTestUtils.setField(service, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(service, "batchMapper", batchMapper);
        ReflectionTestUtils.setField(service, "libraryService", libraryService);
        ReflectionTestUtils.setField(service, "dataScopeService", dataScopeService);
        ReflectionTestUtils.setField(service, "generationPipelineService", pipelineService);
        ReflectionTestUtils.setField(service, "generationProperties", new TkGenerationProperties());
        ReflectionTestUtils.setField(service, "referenceAnalysisService", referenceAnalysisService);
        ReflectionTestUtils.setField(service, "creditService", creditService);
        ReflectionTestUtils.setField(service, "businessLogService", businessLogService);
        ReflectionTestUtils.setField(service, "precheckService", precheckService);
        ReflectionTestUtils.setField(service, "voiceProfileService", voiceProfileService);

        TkGenerationTaskCreateReqVO reqVO = new TkGenerationTaskCreateReqVO();
        reqVO.setLibraryId(10L);
        reqVO.setSourceUrl("https://www.tiktok.com/@demo/video/1");
        reqVO.setReferenceAnalysisId(300L);
        reqVO.setVoiceCode("system-voice");
        TkMaterialLibraryDO library = TkMaterialLibraryDO.builder().id(10L).companyId(20L).name("Demo").build();
        library.setTenantId(8L);
        TkReferenceAnalysisDO analysis = TkReferenceAnalysisDO.builder()
                .id(300L)
                .libraryId(10L)
                .targetLanguage("en")
                .build();
        ReflectionTestUtils.setField(analysis, "businessTraceId", "TRACE-ANALYSIS-001");
        TkGenerationPrecheckRespVO precheck = new TkGenerationPrecheckRespVO();
        precheck.setPassed(true);
        when(libraryService.validateMaterialLibraryReadable(10L)).thenReturn(library);
        when(dataScopeService.getCurrentScope()).thenReturn(new TkUserScope(7L, 8L, "COMPANY_USER", 20L));
        when(precheckService.precheck(reqVO)).thenReturn(precheck);
        when(creditService.freezeForGenerationTask(8L)).thenReturn(900L);
        when(referenceAnalysisService.validateAnalysisReadable(300L)).thenReturn(analysis);
        when(voiceProfileService.resolveVoiceSelection(null, "system-voice")).thenReturn("system-voice");

        service.createGenerationTask(reqVO);

        ArgumentCaptor<TkGenerationTaskDO> captor = ArgumentCaptor.forClass(TkGenerationTaskDO.class);
        verify(taskMapper).insert(captor.capture());
        assertEquals("TRACE-ANALYSIS-001", captor.getValue().getBusinessTraceId());
        verify(pipelineService).submit(8L, captor.getValue().getId());
    }

    @Test
    void createEcommerceTaskUsesDefaultRouteWithoutProductCategoryLookup() {
        TkGenerationTaskServiceImpl service = new TkGenerationTaskServiceImpl();
        TkGenerationTaskMapper taskMapper = mock(TkGenerationTaskMapper.class);
        TkGenerationBatchMapper batchMapper = mock(TkGenerationBatchMapper.class);
        TkMaterialLibraryService libraryService = mock(TkMaterialLibraryService.class);
        TkDataScopeService dataScopeService = mock(TkDataScopeService.class);
        TkGenerationPipelineService pipelineService = mock(TkGenerationPipelineService.class);
        TkReferenceAnalysisService referenceAnalysisService = mock(TkReferenceAnalysisService.class);
        TkCreditService creditService = mock(TkCreditService.class);
        TkBusinessLogService businessLogService = mock(TkBusinessLogService.class);
        TkGenerationPrecheckService precheckService = mock(TkGenerationPrecheckService.class);
        TkVoiceProfileService voiceProfileService = mock(TkVoiceProfileService.class);
        ReflectionTestUtils.setField(service, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(service, "batchMapper", batchMapper);
        ReflectionTestUtils.setField(service, "libraryService", libraryService);
        ReflectionTestUtils.setField(service, "dataScopeService", dataScopeService);
        ReflectionTestUtils.setField(service, "generationPipelineService", pipelineService);
        ReflectionTestUtils.setField(service, "generationProperties", new TkGenerationProperties());
        ReflectionTestUtils.setField(service, "referenceAnalysisService", referenceAnalysisService);
        ReflectionTestUtils.setField(service, "creditService", creditService);
        ReflectionTestUtils.setField(service, "businessLogService", businessLogService);
        ReflectionTestUtils.setField(service, "precheckService", precheckService);
        ReflectionTestUtils.setField(service, "voiceProfileService", voiceProfileService);

        TkGenerationTaskCreateReqVO reqVO = new TkGenerationTaskCreateReqVO();
        reqVO.setLibraryId(10L);
        reqVO.setSourceUrl("https://www.tiktok.com/@demo/video/1");
        reqVO.setMaterialPurpose(TkGeminiPromptConfig.MATERIAL_PURPOSE_ECOMMERCE);
        reqVO.setVoiceCode("system-voice");
        reqVO.setClipPlanMode("SEGMENTED");
        TkMaterialLibraryDO library = TkMaterialLibraryDO.builder().id(10L).companyId(20L).name("Beauty").build();
        library.setTenantId(8L);
        TkGenerationPrecheckRespVO precheck = new TkGenerationPrecheckRespVO();
        precheck.setPassed(true);
        when(libraryService.validateMaterialLibraryReadable(10L)).thenReturn(library);
        when(dataScopeService.getCurrentScope()).thenReturn(new TkUserScope(7L, 8L, "COMPANY_USER", 20L));
        when(precheckService.precheck(reqVO)).thenReturn(precheck);
        when(creditService.freezeForGenerationTask(8L)).thenReturn(900L);
        when(voiceProfileService.resolveVoiceSelection(null, "system-voice")).thenReturn("system-voice");
        service.createGenerationTask(reqVO);

        ArgumentCaptor<TkGenerationTaskDO> captor = ArgumentCaptor.forClass(TkGenerationTaskDO.class);
        verify(taskMapper).insert(captor.capture());
        assertEquals(TkGenerationRouteService.DEFAULT_PRODUCT_CATEGORY_CODE, captor.getValue().getProductCategoryCode());
        assertEquals(TkGenerationRouteService.DEFAULT_ECOMMERCE_ROUTE_CODE, captor.getValue().getGenerationRouteCode());
        assertEquals(TkGenerationRouteConfigSupport.ClipPlanMode.SEGMENTED,
                TkGenerationRouteConfigSupport.resolveClipPlanMode(captor.getValue().getGenerationRouteConfig()));
    }

    @Test
    void createLeadGenerationTaskStoresSelectedFullPoolRandomClipPlanMode() {
        TkGenerationTaskServiceImpl service = new TkGenerationTaskServiceImpl();
        TkGenerationTaskMapper taskMapper = mock(TkGenerationTaskMapper.class);
        TkGenerationBatchMapper batchMapper = mock(TkGenerationBatchMapper.class);
        TkMaterialLibraryService libraryService = mock(TkMaterialLibraryService.class);
        TkDataScopeService dataScopeService = mock(TkDataScopeService.class);
        TkGenerationPipelineService pipelineService = mock(TkGenerationPipelineService.class);
        TkReferenceAnalysisService referenceAnalysisService = mock(TkReferenceAnalysisService.class);
        TkCreditService creditService = mock(TkCreditService.class);
        TkBusinessLogService businessLogService = mock(TkBusinessLogService.class);
        TkGenerationPrecheckService precheckService = mock(TkGenerationPrecheckService.class);
        TkVoiceProfileService voiceProfileService = mock(TkVoiceProfileService.class);
        ReflectionTestUtils.setField(service, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(service, "batchMapper", batchMapper);
        ReflectionTestUtils.setField(service, "libraryService", libraryService);
        ReflectionTestUtils.setField(service, "dataScopeService", dataScopeService);
        ReflectionTestUtils.setField(service, "generationPipelineService", pipelineService);
        ReflectionTestUtils.setField(service, "generationProperties", new TkGenerationProperties());
        ReflectionTestUtils.setField(service, "referenceAnalysisService", referenceAnalysisService);
        ReflectionTestUtils.setField(service, "creditService", creditService);
        ReflectionTestUtils.setField(service, "businessLogService", businessLogService);
        ReflectionTestUtils.setField(service, "precheckService", precheckService);
        ReflectionTestUtils.setField(service, "voiceProfileService", voiceProfileService);

        TkGenerationTaskCreateReqVO reqVO = new TkGenerationTaskCreateReqVO();
        reqVO.setLibraryId(10L);
        reqVO.setMaterialPurpose(TkGeminiPromptConfig.MATERIAL_PURPOSE_LEAD_GENERATION);
        reqVO.setPromptText("lead script");
        reqVO.setVoiceCode("system-voice");
        reqVO.setClipPlanMode("FULL_POOL_RANDOM");
        TkMaterialLibraryDO library = TkMaterialLibraryDO.builder().id(10L).companyId(20L).name("Lead").build();
        library.setTenantId(8L);
        TkGenerationPrecheckRespVO precheck = new TkGenerationPrecheckRespVO();
        precheck.setPassed(true);
        when(libraryService.validateMaterialLibraryReadable(10L)).thenReturn(library);
        when(dataScopeService.getCurrentScope()).thenReturn(new TkUserScope(7L, 8L, "COMPANY_USER", 20L));
        when(precheckService.precheck(reqVO)).thenReturn(precheck);
        when(creditService.freezeForGenerationTask(8L)).thenReturn(900L);
        when(voiceProfileService.resolveVoiceSelection(null, "system-voice")).thenReturn("system-voice");

        service.createGenerationTask(reqVO);

        ArgumentCaptor<TkGenerationTaskDO> captor = ArgumentCaptor.forClass(TkGenerationTaskDO.class);
        verify(taskMapper).insert(captor.capture());
        assertEquals(TkGenerationRouteService.DEFAULT_LEAD_GENERATION_ROUTE_CODE, captor.getValue().getGenerationRouteCode());
        assertEquals(TkGenerationRouteConfigSupport.ClipPlanMode.FULL_POOL_RANDOM,
                TkGenerationRouteConfigSupport.resolveClipPlanMode(captor.getValue().getGenerationRouteConfig()));
    }

    @Test
    void createEcommerceTaskStoresSelectedFullPoolRandomClipPlanModeWithoutRouteLookup() {
        TkGenerationTaskServiceImpl service = new TkGenerationTaskServiceImpl();
        TkGenerationTaskMapper taskMapper = mock(TkGenerationTaskMapper.class);
        TkGenerationBatchMapper batchMapper = mock(TkGenerationBatchMapper.class);
        TkMaterialLibraryService libraryService = mock(TkMaterialLibraryService.class);
        TkDataScopeService dataScopeService = mock(TkDataScopeService.class);
        TkGenerationPipelineService pipelineService = mock(TkGenerationPipelineService.class);
        TkReferenceAnalysisService referenceAnalysisService = mock(TkReferenceAnalysisService.class);
        TkCreditService creditService = mock(TkCreditService.class);
        TkBusinessLogService businessLogService = mock(TkBusinessLogService.class);
        TkGenerationPrecheckService precheckService = mock(TkGenerationPrecheckService.class);
        TkVoiceProfileService voiceProfileService = mock(TkVoiceProfileService.class);
        ReflectionTestUtils.setField(service, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(service, "batchMapper", batchMapper);
        ReflectionTestUtils.setField(service, "libraryService", libraryService);
        ReflectionTestUtils.setField(service, "dataScopeService", dataScopeService);
        ReflectionTestUtils.setField(service, "generationPipelineService", pipelineService);
        ReflectionTestUtils.setField(service, "generationProperties", new TkGenerationProperties());
        ReflectionTestUtils.setField(service, "referenceAnalysisService", referenceAnalysisService);
        ReflectionTestUtils.setField(service, "creditService", creditService);
        ReflectionTestUtils.setField(service, "businessLogService", businessLogService);
        ReflectionTestUtils.setField(service, "precheckService", precheckService);
        ReflectionTestUtils.setField(service, "voiceProfileService", voiceProfileService);

        TkGenerationTaskCreateReqVO reqVO = new TkGenerationTaskCreateReqVO();
        reqVO.setLibraryId(10L);
        reqVO.setSourceUrl("https://www.tiktok.com/@demo/video/1");
        reqVO.setMaterialPurpose(TkGeminiPromptConfig.MATERIAL_PURPOSE_ECOMMERCE);
        reqVO.setVoiceCode("system-voice");
        reqVO.setClipPlanMode("FULL_POOL_RANDOM");
        TkMaterialLibraryDO library = TkMaterialLibraryDO.builder().id(10L).companyId(20L).name("Fashion").build();
        library.setTenantId(8L);
        TkGenerationPrecheckRespVO precheck = new TkGenerationPrecheckRespVO();
        precheck.setPassed(true);
        when(libraryService.validateMaterialLibraryReadable(10L)).thenReturn(library);
        when(dataScopeService.getCurrentScope()).thenReturn(new TkUserScope(7L, 8L, "COMPANY_USER", 20L));
        when(precheckService.precheck(reqVO)).thenReturn(precheck);
        when(creditService.freezeForGenerationTask(8L)).thenReturn(900L);
        when(voiceProfileService.resolveVoiceSelection(null, "system-voice")).thenReturn("system-voice");
        service.createGenerationTask(reqVO);

        ArgumentCaptor<TkGenerationTaskDO> captor = ArgumentCaptor.forClass(TkGenerationTaskDO.class);
        verify(taskMapper).insert(captor.capture());
        assertEquals(TkGenerationRouteService.DEFAULT_ECOMMERCE_ROUTE_CODE, captor.getValue().getGenerationRouteCode());
        assertEquals(TkGenerationRouteConfigSupport.ClipPlanMode.FULL_POOL_RANDOM,
                TkGenerationRouteConfigSupport.resolveClipPlanMode(captor.getValue().getGenerationRouteConfig()));
    }

    @Test
    void createGenerationTasksExpandsScriptsByVideosPerScript() {
        TkGenerationTaskServiceImpl service = new TkGenerationTaskServiceImpl();
        TkGenerationTaskMapper taskMapper = mock(TkGenerationTaskMapper.class);
        TkGenerationBatchMapper batchMapper = mock(TkGenerationBatchMapper.class);
        TkMaterialLibraryService libraryService = mock(TkMaterialLibraryService.class);
        TkDataScopeService dataScopeService = mock(TkDataScopeService.class);
        TkGenerationPipelineService pipelineService = mock(TkGenerationPipelineService.class);
        TkReferenceAnalysisService referenceAnalysisService = mock(TkReferenceAnalysisService.class);
        TkCreditService creditService = mock(TkCreditService.class);
        TkBusinessLogService businessLogService = mock(TkBusinessLogService.class);
        TkGenerationPrecheckService precheckService = mock(TkGenerationPrecheckService.class);
        TkVoiceProfileService voiceProfileService = mock(TkVoiceProfileService.class);
        ReflectionTestUtils.setField(service, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(service, "batchMapper", batchMapper);
        ReflectionTestUtils.setField(service, "libraryService", libraryService);
        ReflectionTestUtils.setField(service, "dataScopeService", dataScopeService);
        ReflectionTestUtils.setField(service, "generationPipelineService", pipelineService);
        ReflectionTestUtils.setField(service, "generationProperties", new TkGenerationProperties());
        ReflectionTestUtils.setField(service, "referenceAnalysisService", referenceAnalysisService);
        ReflectionTestUtils.setField(service, "creditService", creditService);
        ReflectionTestUtils.setField(service, "businessLogService", businessLogService);
        ReflectionTestUtils.setField(service, "precheckService", precheckService);
        ReflectionTestUtils.setField(service, "voiceProfileService", voiceProfileService);

        TkGenerationTaskCreateReqVO reqVO = new TkGenerationTaskCreateReqVO();
        reqVO.setLibraryId(10L);
        reqVO.setSourceUrl("https://www.tiktok.com/@demo/video/1");
        reqVO.setVoiceCode("system-voice");
        reqVO.setScriptOptionIds(Arrays.asList(101L, 102L));
        reqVO.setVideosPerScript(2);
        TkMaterialLibraryDO library = TkMaterialLibraryDO.builder().id(10L).companyId(20L).name("Demo").build();
        library.setTenantId(8L);
        TkGenerationPrecheckRespVO precheck = new TkGenerationPrecheckRespVO();
        precheck.setPassed(true);
        AtomicLong nextId = new AtomicLong(1000L);
        when(libraryService.validateMaterialLibraryReadable(10L)).thenReturn(library);
        when(dataScopeService.getCurrentScope()).thenReturn(new TkUserScope(7L, 8L, "COMPANY_USER", 20L));
        when(precheckService.precheck(any(TkGenerationTaskCreateReqVO.class))).thenReturn(precheck);
        when(creditService.freezeForGenerationTask(8L)).thenReturn(900L, 901L, 902L, 903L);
        when(voiceProfileService.resolveVoiceSelection(null, "system-voice")).thenReturn("system-voice");
        when(referenceAnalysisService.validateScriptOptionReadable(101L)).thenReturn(
                cn.iocoder.yudao.module.tk.dal.dataobject.TkReferenceScriptOptionDO.builder()
                        .id(101L).libraryId(10L).build());
        when(referenceAnalysisService.validateScriptOptionReadable(102L)).thenReturn(
                cn.iocoder.yudao.module.tk.dal.dataobject.TkReferenceScriptOptionDO.builder()
                        .id(102L).libraryId(10L).build());
        org.mockito.Mockito.doAnswer(invocation -> {
            TkGenerationBatchDO batch = invocation.getArgument(0);
            batch.setId(700L);
            return 1;
        }).when(batchMapper).insert(any(TkGenerationBatchDO.class));
        TkGenerationBatchDO batch = TkGenerationBatchDO.builder()
                .id(700L)
                .expectedVideoCount(4)
                .build();
        batch.setTenantId(8L);
        when(batchMapper.selectById(700L)).thenReturn(batch);
        when(taskMapper.selectListByBatchId(any(), any())).thenReturn(java.util.Collections.emptyList());
        org.mockito.Mockito.doAnswer(invocation -> {
            TkGenerationTaskDO task = invocation.getArgument(0);
            task.setId(nextId.getAndIncrement());
            return 1;
        }).when(taskMapper).insert(any(TkGenerationTaskDO.class));

        List<Long> ids = service.createGenerationTasks(reqVO);

        assertEquals(Arrays.asList(1000L, 1001L, 1002L, 1003L), ids);
        ArgumentCaptor<TkGenerationTaskDO> captor = ArgumentCaptor.forClass(TkGenerationTaskDO.class);
        verify(taskMapper, times(4)).insert(captor.capture());
        assertEquals(Arrays.asList(101L, 101L, 102L, 102L),
                captor.getAllValues().stream().map(TkGenerationTaskDO::getScriptOptionId).collect(java.util.stream.Collectors.toList()));
        assertEquals(Arrays.asList(700L, 700L, 700L, 700L),
                captor.getAllValues().stream().map(TkGenerationTaskDO::getBatchId).collect(java.util.stream.Collectors.toList()));
        assertEquals(Arrays.asList(1, 1, 2, 2),
                captor.getAllValues().stream().map(TkGenerationTaskDO::getScriptIndex).collect(java.util.stream.Collectors.toList()));
        assertEquals(Arrays.asList(1, 2, 1, 2),
                captor.getAllValues().stream().map(TkGenerationTaskDO::getVideoIndex).collect(java.util.stream.Collectors.toList()));
        verify(pipelineService).submit(8L, 1000L);
        verify(pipelineService).submit(8L, 1001L);
        verify(pipelineService).submit(8L, 1002L);
        verify(pipelineService).submit(8L, 1003L);
    }

    @Test
    void createLeadGenerationManualScriptTaskDoesNotRequireSourceUrlOrReferenceAnalysis() {
        TkGenerationTaskServiceImpl service = new TkGenerationTaskServiceImpl();
        TkGenerationTaskMapper taskMapper = mock(TkGenerationTaskMapper.class);
        TkGenerationBatchMapper batchMapper = mock(TkGenerationBatchMapper.class);
        TkMaterialLibraryService libraryService = mock(TkMaterialLibraryService.class);
        TkDataScopeService dataScopeService = mock(TkDataScopeService.class);
        TkGenerationPipelineService pipelineService = mock(TkGenerationPipelineService.class);
        TkReferenceAnalysisService referenceAnalysisService = mock(TkReferenceAnalysisService.class);
        TkCreditService creditService = mock(TkCreditService.class);
        TkBusinessLogService businessLogService = mock(TkBusinessLogService.class);
        TkGenerationPrecheckService precheckService = mock(TkGenerationPrecheckService.class);
        TkVoiceProfileService voiceProfileService = mock(TkVoiceProfileService.class);
        ReflectionTestUtils.setField(service, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(service, "batchMapper", batchMapper);
        ReflectionTestUtils.setField(service, "libraryService", libraryService);
        ReflectionTestUtils.setField(service, "dataScopeService", dataScopeService);
        ReflectionTestUtils.setField(service, "generationPipelineService", pipelineService);
        ReflectionTestUtils.setField(service, "generationProperties", new TkGenerationProperties());
        ReflectionTestUtils.setField(service, "referenceAnalysisService", referenceAnalysisService);
        ReflectionTestUtils.setField(service, "creditService", creditService);
        ReflectionTestUtils.setField(service, "businessLogService", businessLogService);
        ReflectionTestUtils.setField(service, "precheckService", precheckService);
        ReflectionTestUtils.setField(service, "voiceProfileService", voiceProfileService);

        TkGenerationTaskCreateReqVO reqVO = new TkGenerationTaskCreateReqVO();
        reqVO.setLibraryId(10L);
        reqVO.setMaterialPurpose(TkGeminiPromptConfig.MATERIAL_PURPOSE_LEAD_GENERATION);
        reqVO.setPromptText("请私信我领取完整方案。");
        reqVO.setVoiceCode("system-voice");
        TkMaterialLibraryDO library = TkMaterialLibraryDO.builder().id(10L).companyId(20L).name("Lead").build();
        library.setTenantId(8L);
        TkGenerationPrecheckRespVO precheck = new TkGenerationPrecheckRespVO();
        precheck.setPassed(true);
        when(libraryService.validateMaterialLibraryReadable(10L)).thenReturn(library);
        when(dataScopeService.getCurrentScope()).thenReturn(new TkUserScope(7L, 8L, "COMPANY_USER", 20L));
        when(precheckService.precheck(reqVO)).thenReturn(precheck);
        when(creditService.freezeForGenerationTask(8L)).thenReturn(900L);
        when(voiceProfileService.resolveVoiceSelection(null, "system-voice")).thenReturn("system-voice");

        service.createGenerationTask(reqVO);

        ArgumentCaptor<TkGenerationTaskDO> captor = ArgumentCaptor.forClass(TkGenerationTaskDO.class);
        verify(taskMapper).insert(captor.capture());
        assertEquals(TkGeminiPromptConfig.MATERIAL_PURPOSE_LEAD_GENERATION, captor.getValue().getMaterialPurpose());
        assertEquals("manual-lead-generation://10", captor.getValue().getSourceUrl());
        assertEquals("请私信我领取完整方案。", captor.getValue().getPromptText());
        assertNull(captor.getValue().getReferenceAnalysisId());
        assertNull(captor.getValue().getScriptOptionId());
    }

    @Test
    void createLeadGenerationManualTaskAllowsBlankPromptAndDisablesVoiceAndSubtitle() {
        TkGenerationTaskServiceImpl service = new TkGenerationTaskServiceImpl();
        TkGenerationTaskMapper taskMapper = mock(TkGenerationTaskMapper.class);
        TkGenerationBatchMapper batchMapper = mock(TkGenerationBatchMapper.class);
        TkMaterialLibraryService libraryService = mock(TkMaterialLibraryService.class);
        TkDataScopeService dataScopeService = mock(TkDataScopeService.class);
        TkGenerationPipelineService pipelineService = mock(TkGenerationPipelineService.class);
        TkReferenceAnalysisService referenceAnalysisService = mock(TkReferenceAnalysisService.class);
        TkCreditService creditService = mock(TkCreditService.class);
        TkBusinessLogService businessLogService = mock(TkBusinessLogService.class);
        TkGenerationPrecheckService precheckService = mock(TkGenerationPrecheckService.class);
        TkVoiceProfileService voiceProfileService = mock(TkVoiceProfileService.class);
        ReflectionTestUtils.setField(service, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(service, "batchMapper", batchMapper);
        ReflectionTestUtils.setField(service, "libraryService", libraryService);
        ReflectionTestUtils.setField(service, "dataScopeService", dataScopeService);
        ReflectionTestUtils.setField(service, "generationPipelineService", pipelineService);
        ReflectionTestUtils.setField(service, "generationProperties", new TkGenerationProperties());
        ReflectionTestUtils.setField(service, "referenceAnalysisService", referenceAnalysisService);
        ReflectionTestUtils.setField(service, "creditService", creditService);
        ReflectionTestUtils.setField(service, "businessLogService", businessLogService);
        ReflectionTestUtils.setField(service, "precheckService", precheckService);
        ReflectionTestUtils.setField(service, "voiceProfileService", voiceProfileService);

        TkGenerationTaskCreateReqVO reqVO = new TkGenerationTaskCreateReqVO();
        reqVO.setLibraryId(10L);
        reqVO.setMaterialPurpose(TkGeminiPromptConfig.MATERIAL_PURPOSE_LEAD_GENERATION);
        reqVO.setPromptText("   ");
        reqVO.setVoiceCode("system-voice");
        reqVO.setVoiceEnabled(true);
        reqVO.setSubtitleEnabled(true);
        TkMaterialLibraryDO library = TkMaterialLibraryDO.builder().id(10L).companyId(20L).name("Lead").build();
        library.setTenantId(8L);
        TkGenerationPrecheckRespVO precheck = new TkGenerationPrecheckRespVO();
        precheck.setPassed(true);
        when(libraryService.validateMaterialLibraryReadable(10L)).thenReturn(library);
        when(dataScopeService.getCurrentScope()).thenReturn(new TkUserScope(7L, 8L, "COMPANY_USER", 20L));
        when(precheckService.precheck(reqVO)).thenReturn(precheck);
        when(creditService.freezeForGenerationTask(8L)).thenReturn(900L);
        when(voiceProfileService.resolveMimoVoiceSelection(any(), any(), any(), any(), any()))
                .thenReturn(new TkMimoVoiceSelection("PRESET", "Mia", "", ""));

        service.createGenerationTask(reqVO);

        ArgumentCaptor<TkGenerationTaskDO> captor = ArgumentCaptor.forClass(TkGenerationTaskDO.class);
        verify(taskMapper).insert(captor.capture());
        assertEquals(TkGeminiPromptConfig.MATERIAL_PURPOSE_LEAD_GENERATION, captor.getValue().getMaterialPurpose());
        assertEquals("manual-lead-generation://10", captor.getValue().getSourceUrl());
        assertEquals("", captor.getValue().getPromptText());
        assertEquals(false, captor.getValue().getVoiceEnabled());
        assertNull(captor.getValue().getVoiceCode());
        assertEquals(false, captor.getValue().getSubtitleEnabled());
        verify(voiceProfileService, never()).resolveVoiceSelection(any(), any());
    }

    @Test
    void createLeadGenerationTaskStoresReadableBgmSelection() {
        TkGenerationTaskServiceImpl service = new TkGenerationTaskServiceImpl();
        TkGenerationTaskMapper taskMapper = mock(TkGenerationTaskMapper.class);
        TkGenerationBatchMapper batchMapper = mock(TkGenerationBatchMapper.class);
        TkMaterialLibraryService libraryService = mock(TkMaterialLibraryService.class);
        TkDataScopeService dataScopeService = mock(TkDataScopeService.class);
        TkGenerationPipelineService pipelineService = mock(TkGenerationPipelineService.class);
        TkReferenceAnalysisService referenceAnalysisService = mock(TkReferenceAnalysisService.class);
        TkCreditService creditService = mock(TkCreditService.class);
        TkBusinessLogService businessLogService = mock(TkBusinessLogService.class);
        TkGenerationPrecheckService precheckService = mock(TkGenerationPrecheckService.class);
        TkVoiceProfileService voiceProfileService = mock(TkVoiceProfileService.class);
        TkBgmAssetService bgmAssetService = mock(TkBgmAssetService.class);
        ReflectionTestUtils.setField(service, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(service, "batchMapper", batchMapper);
        ReflectionTestUtils.setField(service, "libraryService", libraryService);
        ReflectionTestUtils.setField(service, "dataScopeService", dataScopeService);
        ReflectionTestUtils.setField(service, "generationPipelineService", pipelineService);
        ReflectionTestUtils.setField(service, "generationProperties", new TkGenerationProperties());
        ReflectionTestUtils.setField(service, "referenceAnalysisService", referenceAnalysisService);
        ReflectionTestUtils.setField(service, "creditService", creditService);
        ReflectionTestUtils.setField(service, "businessLogService", businessLogService);
        ReflectionTestUtils.setField(service, "precheckService", precheckService);
        ReflectionTestUtils.setField(service, "voiceProfileService", voiceProfileService);
        ReflectionTestUtils.setField(service, "bgmAssetService", bgmAssetService);

        TkGenerationTaskCreateReqVO reqVO = new TkGenerationTaskCreateReqVO();
        reqVO.setLibraryId(10L);
        reqVO.setMaterialPurpose(TkGeminiPromptConfig.MATERIAL_PURPOSE_LEAD_GENERATION);
        reqVO.setPromptText("lead script");
        reqVO.setVoiceCode("system-voice");
        reqVO.setBgmEnabled(true);
        reqVO.setBgmAssetId(88L);
        reqVO.setBgmVolume(0.5D);
        TkMaterialLibraryDO library = TkMaterialLibraryDO.builder().id(10L).companyId(20L).name("Lead").build();
        library.setTenantId(8L);
        TkGenerationPrecheckRespVO precheck = new TkGenerationPrecheckRespVO();
        precheck.setPassed(true);
        TkBgmAssetDO bgm = TkBgmAssetDO.builder()
                .id(88L)
                .sourceType("SYSTEM")
                .fileUrl("https://oss.example.com/bgm.mp3")
                .build();
        when(libraryService.validateMaterialLibraryReadable(10L)).thenReturn(library);
        when(dataScopeService.getCurrentScope()).thenReturn(new TkUserScope(7L, 8L, "COMPANY_USER", 20L));
        when(precheckService.precheck(reqVO)).thenReturn(precheck);
        when(creditService.freezeForGenerationTask(8L)).thenReturn(900L);
        when(voiceProfileService.resolveVoiceSelection(null, "system-voice")).thenReturn("system-voice");
        when(bgmAssetService.validateReadable(88L)).thenReturn(bgm);

        service.createGenerationTask(reqVO);

        ArgumentCaptor<TkGenerationTaskDO> captor = ArgumentCaptor.forClass(TkGenerationTaskDO.class);
        verify(taskMapper).insert(captor.capture());
        assertEquals(true, captor.getValue().getBgmEnabled());
        assertEquals(88L, captor.getValue().getBgmAssetId());
        assertEquals("SYSTEM", captor.getValue().getBgmSourceType());
        assertEquals("https://oss.example.com/bgm.mp3", captor.getValue().getBgmUrl());
        assertEquals(0.30D, captor.getValue().getBgmVolume());
        verify(bgmAssetService).validateReadable(88L);
    }

    @Test
    void createLeadGenerationTaskWithVoiceDisabledDoesNotResolveVoiceAndDisablesSubtitle() {
        TkGenerationTaskServiceImpl service = new TkGenerationTaskServiceImpl();
        TkGenerationTaskMapper taskMapper = mock(TkGenerationTaskMapper.class);
        TkGenerationBatchMapper batchMapper = mock(TkGenerationBatchMapper.class);
        TkMaterialLibraryService libraryService = mock(TkMaterialLibraryService.class);
        TkDataScopeService dataScopeService = mock(TkDataScopeService.class);
        TkGenerationPipelineService pipelineService = mock(TkGenerationPipelineService.class);
        TkReferenceAnalysisService referenceAnalysisService = mock(TkReferenceAnalysisService.class);
        TkCreditService creditService = mock(TkCreditService.class);
        TkBusinessLogService businessLogService = mock(TkBusinessLogService.class);
        TkGenerationPrecheckService precheckService = mock(TkGenerationPrecheckService.class);
        TkVoiceProfileService voiceProfileService = mock(TkVoiceProfileService.class);
        ReflectionTestUtils.setField(service, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(service, "batchMapper", batchMapper);
        ReflectionTestUtils.setField(service, "libraryService", libraryService);
        ReflectionTestUtils.setField(service, "dataScopeService", dataScopeService);
        ReflectionTestUtils.setField(service, "generationPipelineService", pipelineService);
        ReflectionTestUtils.setField(service, "generationProperties", new TkGenerationProperties());
        ReflectionTestUtils.setField(service, "referenceAnalysisService", referenceAnalysisService);
        ReflectionTestUtils.setField(service, "creditService", creditService);
        ReflectionTestUtils.setField(service, "businessLogService", businessLogService);
        ReflectionTestUtils.setField(service, "precheckService", precheckService);
        ReflectionTestUtils.setField(service, "voiceProfileService", voiceProfileService);

        TkGenerationTaskCreateReqVO reqVO = new TkGenerationTaskCreateReqVO();
        reqVO.setLibraryId(10L);
        reqVO.setMaterialPurpose(TkGeminiPromptConfig.MATERIAL_PURPOSE_LEAD_GENERATION);
        reqVO.setPromptText("lead script");
        reqVO.setVoiceEnabled(false);
        reqVO.setSubtitleEnabled(true);
        TkMaterialLibraryDO library = TkMaterialLibraryDO.builder().id(10L).companyId(20L).name("Lead").build();
        library.setTenantId(8L);
        TkGenerationPrecheckRespVO precheck = new TkGenerationPrecheckRespVO();
        precheck.setPassed(true);
        when(libraryService.validateMaterialLibraryReadable(10L)).thenReturn(library);
        when(dataScopeService.getCurrentScope()).thenReturn(new TkUserScope(7L, 8L, "COMPANY_USER", 20L));
        when(precheckService.precheck(reqVO)).thenReturn(precheck);
        when(creditService.freezeForGenerationTask(8L)).thenReturn(900L);
        when(voiceProfileService.resolveMimoVoiceSelection(any(), any(), any(), any(), any()))
                .thenReturn(new TkMimoVoiceSelection("PRESET", "Mia", "", ""));

        service.createGenerationTask(reqVO);

        ArgumentCaptor<TkGenerationTaskDO> captor = ArgumentCaptor.forClass(TkGenerationTaskDO.class);
        verify(taskMapper).insert(captor.capture());
        assertEquals(false, captor.getValue().getVoiceEnabled());
        assertNull(captor.getValue().getVoiceCode());
        assertEquals(false, captor.getValue().getSubtitleEnabled());
        verify(voiceProfileService, never()).resolveVoiceSelection(any(), any());
    }

    @Test
    void createEcommerceTaskIgnoresVoiceDisabledRequest() {
        TkGenerationTaskServiceImpl service = new TkGenerationTaskServiceImpl();
        TkGenerationTaskMapper taskMapper = mock(TkGenerationTaskMapper.class);
        TkGenerationBatchMapper batchMapper = mock(TkGenerationBatchMapper.class);
        TkMaterialLibraryService libraryService = mock(TkMaterialLibraryService.class);
        TkDataScopeService dataScopeService = mock(TkDataScopeService.class);
        TkGenerationPipelineService pipelineService = mock(TkGenerationPipelineService.class);
        TkReferenceAnalysisService referenceAnalysisService = mock(TkReferenceAnalysisService.class);
        TkCreditService creditService = mock(TkCreditService.class);
        TkBusinessLogService businessLogService = mock(TkBusinessLogService.class);
        TkGenerationPrecheckService precheckService = mock(TkGenerationPrecheckService.class);
        TkVoiceProfileService voiceProfileService = mock(TkVoiceProfileService.class);
        ReflectionTestUtils.setField(service, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(service, "batchMapper", batchMapper);
        ReflectionTestUtils.setField(service, "libraryService", libraryService);
        ReflectionTestUtils.setField(service, "dataScopeService", dataScopeService);
        ReflectionTestUtils.setField(service, "generationPipelineService", pipelineService);
        ReflectionTestUtils.setField(service, "generationProperties", new TkGenerationProperties());
        ReflectionTestUtils.setField(service, "referenceAnalysisService", referenceAnalysisService);
        ReflectionTestUtils.setField(service, "creditService", creditService);
        ReflectionTestUtils.setField(service, "businessLogService", businessLogService);
        ReflectionTestUtils.setField(service, "precheckService", precheckService);
        ReflectionTestUtils.setField(service, "voiceProfileService", voiceProfileService);

        TkGenerationTaskCreateReqVO reqVO = new TkGenerationTaskCreateReqVO();
        reqVO.setLibraryId(10L);
        reqVO.setSourceUrl("https://www.tiktok.com/@demo/video/1");
        reqVO.setMaterialPurpose(TkGeminiPromptConfig.MATERIAL_PURPOSE_ECOMMERCE);
        reqVO.setVoiceCode("system-voice");
        reqVO.setVoiceEnabled(false);
        reqVO.setSubtitleEnabled(false);
        TkMaterialLibraryDO library = TkMaterialLibraryDO.builder().id(10L).companyId(20L).name("Shop").build();
        library.setTenantId(8L);
        TkGenerationPrecheckRespVO precheck = new TkGenerationPrecheckRespVO();
        precheck.setPassed(true);
        when(libraryService.validateMaterialLibraryReadable(10L)).thenReturn(library);
        when(dataScopeService.getCurrentScope()).thenReturn(new TkUserScope(7L, 8L, "COMPANY_USER", 20L));
        when(precheckService.precheck(reqVO)).thenReturn(precheck);
        when(creditService.freezeForGenerationTask(8L)).thenReturn(900L);
        when(voiceProfileService.resolveVoiceSelection(null, "system-voice")).thenReturn("system-voice");

        service.createGenerationTask(reqVO);

        ArgumentCaptor<TkGenerationTaskDO> captor = ArgumentCaptor.forClass(TkGenerationTaskDO.class);
        verify(taskMapper).insert(captor.capture());
        assertEquals(true, captor.getValue().getVoiceEnabled());
        assertEquals("system-voice", captor.getValue().getVoiceCode());
        assertEquals(false, captor.getValue().getSubtitleEnabled());
        verify(voiceProfileService).resolveVoiceSelection(null, "system-voice");
    }

    @Test
    void createEcommerceTaskIgnoresBgmSelection() {
        TkGenerationTaskServiceImpl service = new TkGenerationTaskServiceImpl();
        TkGenerationTaskMapper taskMapper = mock(TkGenerationTaskMapper.class);
        TkGenerationBatchMapper batchMapper = mock(TkGenerationBatchMapper.class);
        TkMaterialLibraryService libraryService = mock(TkMaterialLibraryService.class);
        TkDataScopeService dataScopeService = mock(TkDataScopeService.class);
        TkGenerationPipelineService pipelineService = mock(TkGenerationPipelineService.class);
        TkReferenceAnalysisService referenceAnalysisService = mock(TkReferenceAnalysisService.class);
        TkCreditService creditService = mock(TkCreditService.class);
        TkBusinessLogService businessLogService = mock(TkBusinessLogService.class);
        TkGenerationPrecheckService precheckService = mock(TkGenerationPrecheckService.class);
        TkVoiceProfileService voiceProfileService = mock(TkVoiceProfileService.class);
        TkBgmAssetService bgmAssetService = mock(TkBgmAssetService.class);
        ReflectionTestUtils.setField(service, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(service, "batchMapper", batchMapper);
        ReflectionTestUtils.setField(service, "libraryService", libraryService);
        ReflectionTestUtils.setField(service, "dataScopeService", dataScopeService);
        ReflectionTestUtils.setField(service, "generationPipelineService", pipelineService);
        ReflectionTestUtils.setField(service, "generationProperties", new TkGenerationProperties());
        ReflectionTestUtils.setField(service, "referenceAnalysisService", referenceAnalysisService);
        ReflectionTestUtils.setField(service, "creditService", creditService);
        ReflectionTestUtils.setField(service, "businessLogService", businessLogService);
        ReflectionTestUtils.setField(service, "precheckService", precheckService);
        ReflectionTestUtils.setField(service, "voiceProfileService", voiceProfileService);
        ReflectionTestUtils.setField(service, "bgmAssetService", bgmAssetService);

        TkGenerationTaskCreateReqVO reqVO = new TkGenerationTaskCreateReqVO();
        reqVO.setLibraryId(10L);
        reqVO.setSourceUrl("https://www.tiktok.com/@demo/video/1");
        reqVO.setMaterialPurpose(TkGeminiPromptConfig.MATERIAL_PURPOSE_ECOMMERCE);
        reqVO.setVoiceCode("system-voice");
        reqVO.setBgmEnabled(true);
        reqVO.setBgmAssetId(88L);
        TkMaterialLibraryDO library = TkMaterialLibraryDO.builder().id(10L).companyId(20L).name("Shop").build();
        library.setTenantId(8L);
        TkGenerationPrecheckRespVO precheck = new TkGenerationPrecheckRespVO();
        precheck.setPassed(true);
        when(libraryService.validateMaterialLibraryReadable(10L)).thenReturn(library);
        when(dataScopeService.getCurrentScope()).thenReturn(new TkUserScope(7L, 8L, "COMPANY_USER", 20L));
        when(precheckService.precheck(reqVO)).thenReturn(precheck);
        when(creditService.freezeForGenerationTask(8L)).thenReturn(900L);
        when(voiceProfileService.resolveVoiceSelection(null, "system-voice")).thenReturn("system-voice");

        service.createGenerationTask(reqVO);

        ArgumentCaptor<TkGenerationTaskDO> captor = ArgumentCaptor.forClass(TkGenerationTaskDO.class);
        verify(taskMapper).insert(captor.capture());
        assertEquals(false, captor.getValue().getBgmEnabled());
        assertNull(captor.getValue().getBgmAssetId());
        assertNull(captor.getValue().getBgmUrl());
        verify(bgmAssetService, never()).validateReadable(88L);
    }

    @Test
    void createEcommerceTaskStoresMimoProviderFieldsWithoutTouchingDashScopeVoiceProfile() {
        TkGenerationTaskServiceImpl service = new TkGenerationTaskServiceImpl();
        TkGenerationTaskMapper taskMapper = mock(TkGenerationTaskMapper.class);
        TkGenerationBatchMapper batchMapper = mock(TkGenerationBatchMapper.class);
        TkMaterialLibraryService libraryService = mock(TkMaterialLibraryService.class);
        TkDataScopeService dataScopeService = mock(TkDataScopeService.class);
        TkGenerationPipelineService pipelineService = mock(TkGenerationPipelineService.class);
        TkReferenceAnalysisService referenceAnalysisService = mock(TkReferenceAnalysisService.class);
        TkCreditService creditService = mock(TkCreditService.class);
        TkBusinessLogService businessLogService = mock(TkBusinessLogService.class);
        TkGenerationPrecheckService precheckService = mock(TkGenerationPrecheckService.class);
        TkVoiceProfileService voiceProfileService = mock(TkVoiceProfileService.class);
        ReflectionTestUtils.setField(service, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(service, "batchMapper", batchMapper);
        ReflectionTestUtils.setField(service, "libraryService", libraryService);
        ReflectionTestUtils.setField(service, "dataScopeService", dataScopeService);
        ReflectionTestUtils.setField(service, "generationPipelineService", pipelineService);
        ReflectionTestUtils.setField(service, "generationProperties", new TkGenerationProperties());
        ReflectionTestUtils.setField(service, "referenceAnalysisService", referenceAnalysisService);
        ReflectionTestUtils.setField(service, "creditService", creditService);
        ReflectionTestUtils.setField(service, "businessLogService", businessLogService);
        ReflectionTestUtils.setField(service, "precheckService", precheckService);
        ReflectionTestUtils.setField(service, "voiceProfileService", voiceProfileService);

        TkGenerationTaskCreateReqVO reqVO = new TkGenerationTaskCreateReqVO();
        reqVO.setLibraryId(10L);
        reqVO.setSourceUrl("https://www.tiktok.com/@demo/video/1");
        reqVO.setMaterialPurpose(TkGeminiPromptConfig.MATERIAL_PURPOSE_ECOMMERCE);
        reqVO.setTtsProvider("mimo");
        reqVO.setMimoVoiceMode("preset");
        reqVO.setMimoVoiceCode("Mia");
        reqVO.setVoiceEnabled(true);
        reqVO.setSubtitleEnabled(false);
        TkMaterialLibraryDO library = TkMaterialLibraryDO.builder().id(10L).companyId(20L).name("Shop").build();
        library.setTenantId(8L);
        TkGenerationPrecheckRespVO precheck = new TkGenerationPrecheckRespVO();
        precheck.setPassed(true);
        when(libraryService.validateMaterialLibraryReadable(10L)).thenReturn(library);
        when(dataScopeService.getCurrentScope()).thenReturn(new TkUserScope(7L, 8L, "COMPANY_USER", 20L));
        when(precheckService.precheck(reqVO)).thenReturn(precheck);
        when(creditService.freezeForGenerationTask(8L)).thenReturn(900L);
        when(voiceProfileService.resolveMimoVoiceSelection(any(), any(), any(), any(), any()))
                .thenReturn(new TkMimoVoiceSelection("PRESET", "Mia", "", ""));

        service.createGenerationTask(reqVO);

        ArgumentCaptor<TkGenerationTaskDO> captor = ArgumentCaptor.forClass(TkGenerationTaskDO.class);
        verify(taskMapper).insert(captor.capture());
        assertEquals("MIMO", captor.getValue().getTtsProvider());
        assertNull(captor.getValue().getVoiceCode());
        assertEquals("PRESET", captor.getValue().getMimoVoiceMode());
        assertEquals("Mia", captor.getValue().getMimoVoiceCode());
        assertEquals("", captor.getValue().getMimoVoicePrompt());
        assertEquals("", captor.getValue().getMimoVoiceSampleUrl());
        verify(voiceProfileService, never()).resolveVoiceSelection(any(), any());
    }

}

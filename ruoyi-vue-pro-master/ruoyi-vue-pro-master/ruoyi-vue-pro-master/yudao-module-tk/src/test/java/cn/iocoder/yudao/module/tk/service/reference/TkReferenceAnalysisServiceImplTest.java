package cn.iocoder.yudao.module.tk.service.reference;

import cn.iocoder.yudao.module.tk.controller.admin.reference.vo.TkReferenceAnalyzeReqVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkMaterialLibraryDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkReferenceAnalysisDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkReferenceScriptOptionDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkReferenceAnalysisMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.TkReferenceScriptOptionMapper;
import cn.iocoder.yudao.module.tk.enums.TkApiKeyProviderEnum;
import cn.iocoder.yudao.module.tk.service.config.TkApiKeyConfigService;
import cn.iocoder.yudao.module.tk.service.credit.TkCreditService;
import cn.iocoder.yudao.module.tk.service.generation.pipeline.TkAiImageInput;
import cn.iocoder.yudao.module.tk.service.generation.pipeline.TkGeminiClient;
import cn.iocoder.yudao.module.tk.service.generation.pipeline.TkGeminiPromptConfig;
import cn.iocoder.yudao.module.tk.service.log.TkBusinessLogService;
import cn.iocoder.yudao.module.tk.service.material.TkMaterialLibraryService;
import cn.iocoder.yudao.module.tk.service.reference.ai.TkReferenceAiAnalysisClient;
import cn.iocoder.yudao.module.tk.service.reference.ai.TkReferenceAiAnalysisContext;
import cn.iocoder.yudao.module.tk.service.reference.ai.TkReferenceAiAnalysisResult;
import cn.iocoder.yudao.module.tk.service.reference.ai.TkReferenceAiAnalysisRouter;
import cn.iocoder.yudao.module.tk.service.scope.TkDataScopeService;
import cn.iocoder.yudao.module.tk.service.scope.TkUserScope;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TkReferenceAnalysisServiceImplTest {

    @Test
    void validateScriptOptionForGenerationUsesTaskOwnershipWithoutUserScope() {
        TkReferenceAnalysisServiceImpl service = new TkReferenceAnalysisServiceImpl();
        TkReferenceScriptOptionMapper scriptOptionMapper = mock(TkReferenceScriptOptionMapper.class);
        TkDataScopeService dataScopeService = mock(TkDataScopeService.class);
        ReflectionTestUtils.setField(service, "scriptOptionMapper", scriptOptionMapper);
        ReflectionTestUtils.setField(service, "dataScopeService", dataScopeService);
        TkReferenceScriptOptionDO option = TkReferenceScriptOptionDO.builder()
                .id(1267L)
                .analysisId(170L)
                .companyId(166L)
                .libraryId(29L)
                .build();
        option.setTenantId(166L);
        when(scriptOptionMapper.selectById(1267L)).thenReturn(option);

        assertEquals(option, service.validateScriptOptionForGeneration(1267L, 166L, 166L, 29L, 170L));

        verifyNoInteractions(dataScopeService);
    }

    @Test
    void validateScriptOptionForGenerationRejectsDifferentTaskOwnership() {
        TkReferenceAnalysisServiceImpl service = new TkReferenceAnalysisServiceImpl();
        TkReferenceScriptOptionMapper scriptOptionMapper = mock(TkReferenceScriptOptionMapper.class);
        ReflectionTestUtils.setField(service, "scriptOptionMapper", scriptOptionMapper);
        TkReferenceScriptOptionDO option = TkReferenceScriptOptionDO.builder()
                .id(1267L)
                .analysisId(170L)
                .companyId(166L)
                .libraryId(29L)
                .build();
        option.setTenantId(166L);
        when(scriptOptionMapper.selectById(1267L)).thenReturn(option);

        assertThrows(RuntimeException.class,
                () -> service.validateScriptOptionForGeneration(1267L, 166L, 166L, 30L, 170L));
    }

    @Test
    void analyzeCreatesWaitingAnalysisAndSubmitsAsyncWorker() {
        TkReferenceAnalysisServiceImpl service = new TkReferenceAnalysisServiceImpl();
        TkMaterialLibraryService materialLibraryService = mock(TkMaterialLibraryService.class);
        TkDataScopeService dataScopeService = mock(TkDataScopeService.class);
        TkReferenceAnalysisMapper analysisMapper = mock(TkReferenceAnalysisMapper.class);
        TkReferenceScriptOptionMapper scriptOptionMapper = mock(TkReferenceScriptOptionMapper.class);
        TkCreditService creditService = mock(TkCreditService.class);
        TkBusinessLogService businessLogService = mock(TkBusinessLogService.class);
        TkReferenceVideoContentService videoContentService = mock(TkReferenceVideoContentService.class);
        CapturingExecutorService executorService = new CapturingExecutorService();
        ReflectionTestUtils.setField(service, "materialLibraryService", materialLibraryService);
        ReflectionTestUtils.setField(service, "dataScopeService", dataScopeService);
        ReflectionTestUtils.setField(service, "analysisMapper", analysisMapper);
        ReflectionTestUtils.setField(service, "scriptOptionMapper", scriptOptionMapper);
        ReflectionTestUtils.setField(service, "creditService", creditService);
        ReflectionTestUtils.setField(service, "businessLogService", businessLogService);
        ReflectionTestUtils.setField(service, "referenceVideoContentService", videoContentService);
        ReflectionTestUtils.setField(service, "executorService", executorService);

        TkReferenceAnalyzeReqVO reqVO = new TkReferenceAnalyzeReqVO();
        reqVO.setLibraryId(10L);
        reqVO.setSourceUrl("https://www.tiktok.com/@demo/video/1");
        reqVO.setTargetLanguage("en");
        TkMaterialLibraryDO library = TkMaterialLibraryDO.builder()
                .id(10L)
                .companyId(20L)
                .name("Demo")
                .build();
        library.setTenantId(8L);
        TkUserScope scope = new TkUserScope(7L, 8L, "COMPANY_USER", 20L);
        when(materialLibraryService.validateMaterialLibraryReadable(10L)).thenReturn(library);
        when(dataScopeService.getCurrentScope()).thenReturn(scope);
        when(creditService.freezeForReferenceAnalysis(8L)).thenReturn(900L);
        when(scriptOptionMapper.selectListByAnalysisId(100L)).thenReturn(Collections.emptyList());
        doAnswer(invocation -> {
            TkReferenceAnalysisDO analysis = invocation.getArgument(0);
            analysis.setId(100L);
            return 1;
        }).when(analysisMapper).insert(any(TkReferenceAnalysisDO.class));

        assertEquals("WAITING", service.analyze(reqVO).getStatus());

        ArgumentCaptor<TkReferenceAnalysisDO> captor = ArgumentCaptor.forClass(TkReferenceAnalysisDO.class);
        verify(analysisMapper).insert(captor.capture());
        assertEquals(100L, captor.getValue().getId());
        assertEquals("WAITING", captor.getValue().getStatus());
        assertEquals("https://www.tiktok.com/@demo/video/1", captor.getValue().getSourceUrl());
        verify(creditService).bindBusiness(900L, 100L);
        assertEquals(1, executorService.getSubmittedCount());
        verify(videoContentService, never()).analyze(any(String.class), anyLong());
        verify(analysisMapper).selectLatestReusable(eq(10L), eq(reqVO.getSourceUrl()), eq("en"), eq(15),
                eq(TkGeminiPromptConfig.MATERIAL_PURPOSE_ECOMMERCE), eq(TkReferenceAnalysisProvider.GEMINI), eq(scope));
    }

    @Test
    void analyzeReusesSuccessfulAnalysisWithoutFreezingCredits() {
        TkReferenceAnalysisServiceImpl service = new TkReferenceAnalysisServiceImpl();
        TkMaterialLibraryService materialLibraryService = mock(TkMaterialLibraryService.class);
        TkDataScopeService dataScopeService = mock(TkDataScopeService.class);
        TkReferenceAnalysisMapper analysisMapper = mock(TkReferenceAnalysisMapper.class);
        TkReferenceScriptOptionMapper scriptOptionMapper = mock(TkReferenceScriptOptionMapper.class);
        TkCreditService creditService = mock(TkCreditService.class);
        TkBusinessLogService businessLogService = mock(TkBusinessLogService.class);
        CapturingExecutorService executorService = new CapturingExecutorService();
        ReflectionTestUtils.setField(service, "materialLibraryService", materialLibraryService);
        ReflectionTestUtils.setField(service, "dataScopeService", dataScopeService);
        ReflectionTestUtils.setField(service, "analysisMapper", analysisMapper);
        ReflectionTestUtils.setField(service, "scriptOptionMapper", scriptOptionMapper);
        ReflectionTestUtils.setField(service, "creditService", creditService);
        ReflectionTestUtils.setField(service, "businessLogService", businessLogService);
        ReflectionTestUtils.setField(service, "executorService", executorService);

        TkReferenceAnalyzeReqVO reqVO = new TkReferenceAnalyzeReqVO();
        reqVO.setLibraryId(10L);
        reqVO.setSourceUrl("https://www.tiktok.com/@demo/video/1");
        reqVO.setTargetLanguage("en");
        TkMaterialLibraryDO library = TkMaterialLibraryDO.builder()
                .id(10L)
                .companyId(20L)
                .name("Demo")
                .build();
        library.setTenantId(8L);
        TkUserScope scope = new TkUserScope(7L, 8L, "COMPANY_USER", 20L);
        TkReferenceAnalysisDO cached = TkReferenceAnalysisDO.builder()
                .id(100L)
                .businessTraceId("TRACE-CACHED")
                .libraryId(10L)
                .sourceUrl(reqVO.getSourceUrl())
                .targetLanguage("en")
                .referenceDuration(15)
                .resolvedVideoUrl("https://cdn.example.com/reference.mp4")
                .coverUrl("https://cdn.example.com/reference.jpg")
                .productName("Demo")
                .coreSellingPoints("[\"stable\"]")
                .targetAudience("[\"creator\"]")
                .usageScenarios("[\"demo\"]")
                .videoStructure("[\"hook\",\"demo\",\"cta\"]")
                .status("SUCCESS")
                .analysisResult("{\"product\":\"Demo\"}")
                .sellingPoints("[\"stable\"]")
                .build();
        cached.setTenantId(8L);

        when(materialLibraryService.validateMaterialLibraryReadable(10L)).thenReturn(library);
        when(dataScopeService.getCurrentScope()).thenReturn(scope);
        when(analysisMapper.selectLatestReusable(10L, reqVO.getSourceUrl(), "en", 15,
                TkGeminiPromptConfig.MATERIAL_PURPOSE_ECOMMERCE, TkReferenceAnalysisProvider.GEMINI, scope)).thenReturn(cached);
        when(scriptOptionMapper.selectListByAnalysisId(100L)).thenReturn(Collections.emptyList());

        assertEquals("SUCCESS", service.analyze(reqVO).getStatus());

        verify(creditService, never()).freezeForReferenceAnalysis(8L);
        verify(creditService, never()).bindBusiness(anyLong(), anyLong());
        verify(creditService, never()).settleByLogId(anyLong());
        verify(analysisMapper, never()).insert(any(TkReferenceAnalysisDO.class));
        assertEquals(0, executorService.getSubmittedCount());
    }

    @Test
    void analyzeReusesRunningAnalysisWithoutCreatingDuplicateTask() {
        TkReferenceAnalysisServiceImpl service = new TkReferenceAnalysisServiceImpl();
        TkMaterialLibraryService materialLibraryService = mock(TkMaterialLibraryService.class);
        TkDataScopeService dataScopeService = mock(TkDataScopeService.class);
        TkReferenceAnalysisMapper analysisMapper = mock(TkReferenceAnalysisMapper.class);
        TkReferenceScriptOptionMapper scriptOptionMapper = mock(TkReferenceScriptOptionMapper.class);
        TkCreditService creditService = mock(TkCreditService.class);
        TkBusinessLogService businessLogService = mock(TkBusinessLogService.class);
        CapturingExecutorService executorService = new CapturingExecutorService();
        ReflectionTestUtils.setField(service, "materialLibraryService", materialLibraryService);
        ReflectionTestUtils.setField(service, "dataScopeService", dataScopeService);
        ReflectionTestUtils.setField(service, "analysisMapper", analysisMapper);
        ReflectionTestUtils.setField(service, "scriptOptionMapper", scriptOptionMapper);
        ReflectionTestUtils.setField(service, "creditService", creditService);
        ReflectionTestUtils.setField(service, "businessLogService", businessLogService);
        ReflectionTestUtils.setField(service, "executorService", executorService);

        TkReferenceAnalyzeReqVO reqVO = new TkReferenceAnalyzeReqVO();
        reqVO.setLibraryId(10L);
        reqVO.setSourceUrl("https://www.tiktok.com/@demo/video/1");
        reqVO.setTargetLanguage("en");
        TkMaterialLibraryDO library = TkMaterialLibraryDO.builder()
                .id(10L)
                .companyId(20L)
                .name("Demo")
                .build();
        library.setTenantId(8L);
        TkUserScope scope = new TkUserScope(7L, 8L, "COMPANY_USER", 20L);
        TkReferenceAnalysisDO running = TkReferenceAnalysisDO.builder()
                .id(101L)
                .businessTraceId("TRACE-RUNNING")
                .libraryId(10L)
                .sourceUrl(reqVO.getSourceUrl())
                .targetLanguage("en")
                .referenceDuration(15)
                .productName("Demo")
                .status("RUNNING")
                .build();
        running.setTenantId(8L);

        when(materialLibraryService.validateMaterialLibraryReadable(10L)).thenReturn(library);
        when(dataScopeService.getCurrentScope()).thenReturn(scope);
        when(analysisMapper.selectLatestReusable(10L, reqVO.getSourceUrl(), "en", 15,
                TkGeminiPromptConfig.MATERIAL_PURPOSE_ECOMMERCE, TkReferenceAnalysisProvider.GEMINI, scope)).thenReturn(running);
        when(scriptOptionMapper.selectListByAnalysisId(101L)).thenReturn(Collections.emptyList());

        assertEquals("RUNNING", service.analyze(reqVO).getStatus());

        verify(creditService, never()).freezeForReferenceAnalysis(8L);
        verify(analysisMapper, never()).insert(any(TkReferenceAnalysisDO.class));
        assertEquals(0, executorService.getSubmittedCount());
    }

    @Test
    void saveFailedAnalysisKeepsResolvedVideoMetadataWhenAiTimesOutAfterVideoParsed() throws Exception {
        TkReferenceAnalysisServiceImpl service = new TkReferenceAnalysisServiceImpl();
        ReflectionTestUtils.setField(service, "geminiClient", new TimeoutGeminiClient());
        ReflectionTestUtils.setField(service, "referenceVideoContentService", new ParsedVideoContentService());
        ReflectionTestUtils.setField(service, "apiKeyConfigService", new DefaultConfigService());
        ReflectionTestUtils.setField(service, "referenceAiAnalysisRouter", new TkReferenceAiAnalysisRouter(
                Collections.singletonList(new TkReferenceAiAnalysisClient() {
                    @Override
                    public String provider() {
                        return TkReferenceAnalysisProvider.GEMINI;
                    }

                    @Override
                    public TkReferenceAiAnalysisResult analyze(TkReferenceAiAnalysisContext context) {
                        throw new IllegalStateException("Read timed out");
                    }
                })));
        TkReferenceAnalysisMapper analysisMapper = mock(TkReferenceAnalysisMapper.class);
        when(analysisMapper.insert(any(TkReferenceAnalysisDO.class))).thenReturn(1);
        ReflectionTestUtils.setField(service, "analysisMapper", analysisMapper);

        TkReferenceAnalyzeReqVO reqVO = new TkReferenceAnalyzeReqVO();
        reqVO.setLibraryId(10L);
        reqVO.setSourceUrl("https://www.tiktok.com/@ccnews66/video/7647901196526587157");
        reqVO.setTargetLanguage("en");
        TkMaterialLibraryDO library = TkMaterialLibraryDO.builder()
                .id(10L)
                .name("脚步提拉带")
                .build();

        Exception ex = invokeGenerateDraftExpectException(service, reqVO, library);
        TkReferenceAnalysisDO failed = invokeSaveFailedAnalysis(service, "TRACE-FAILED-001", reqVO, library, 166L, 166L, ex);

        assertEquals("https://example.com/reference.mp4", failed.getResolvedVideoUrl());
        assertEquals("https://example.com/reference.jpg", failed.getCoverUrl());
        assertEquals(25, failed.getVideoDuration());
        assertTrue(failed.getFailReason().contains("Read timed out"));
    }

    @Test
    void saveFailedAnalysisPersistsBusinessTraceId() throws Exception {
        TkReferenceAnalysisServiceImpl service = new TkReferenceAnalysisServiceImpl();
        TkReferenceAnalysisMapper analysisMapper = mock(TkReferenceAnalysisMapper.class);
        when(analysisMapper.insert(any(TkReferenceAnalysisDO.class))).thenReturn(1);
        ReflectionTestUtils.setField(service, "analysisMapper", analysisMapper);

        TkReferenceAnalyzeReqVO reqVO = new TkReferenceAnalyzeReqVO();
        reqVO.setLibraryId(10L);
        reqVO.setSourceUrl("https://www.tiktok.com/@demo/video/1");
        reqVO.setTargetLanguage("en");
        TkMaterialLibraryDO library = TkMaterialLibraryDO.builder()
                .id(10L)
                .name("Demo")
                .build();

        TkReferenceAnalysisDO failed = invokeSaveFailedAnalysis(service, "TRACE-ANALYSIS-001",
                reqVO, library, 166L, 166L, new IllegalStateException("AI failed"));

        assertEquals("TRACE-ANALYSIS-001", failed.getBusinessTraceId());
    }

    @Test
    void generateDraftParsesVideoDurationStringWithUnit() throws Exception {
        Object result = invokeGenerateDraftWithAiContent(validAnalysisJson("\"33秒\""), 33L);
        Object draft = ReflectionTestUtils.getField(result, "draft");

        assertEquals(33, ReflectionTestUtils.getField(draft, "videoDuration"));
    }

    @Test
    void generateDraftUsesParsedVideoDurationWhenAiDurationIsBlank() throws Exception {
        Object result = invokeGenerateDraftWithAiContent(validAnalysisJson("\"\""), 33L);
        Object draft = ReflectionTestUtils.getField(result, "draft");

        assertEquals(33, ReflectionTestUtils.getField(draft, "videoDuration"));
    }

    @Test
    void generateDraftSplitsStringListFieldsReturnedByAi() throws Exception {
        String aiContent = validAnalysisJson("33")
                .replace("\"coreSellingPoints\":[\"爆汁\",\"现摘\",\"桃香\"]",
                        "\"coreSellingPoints\":\"爆汁｜现摘｜桃香\"")
                .replace("\"targetAudience\":[\"爱吃水果的人\"]",
                        "\"targetAudience\":\"水果爱好者、家庭用户\"")
                .replace("\"usageScenarios\":[\"家庭分享\"]",
                        "\"usageScenarios\":\"家庭分享，朋友聚会\"")
                .replace("\"videoStructure\":[\"开头\",\"展示\",\"结尾\"]",
                        "\"videoStructure\":\"开头 -> 展示 -> 结尾\"")
                .replace("\"displayTargetAudienceZh\":[\"爱吃水果的人\"]",
                        "\"displayTargetAudienceZh\":\"水果爱好者、家庭用户\"");
        Object result = invokeGenerateDraftWithAiContent(aiContent, 33L);
        Object draft = ReflectionTestUtils.getField(result, "draft");

        assertEquals(Arrays.asList("爆汁", "现摘", "桃香"),
                ReflectionTestUtils.getField(draft, "coreSellingPoints"));
        assertEquals(Arrays.asList("水果爱好者", "家庭用户"),
                ReflectionTestUtils.getField(draft, "targetAudience"));
        assertEquals(Arrays.asList("家庭分享", "朋友聚会"),
                ReflectionTestUtils.getField(draft, "usageScenarios"));
        assertEquals(Arrays.asList("开头", "展示", "结尾"),
                ReflectionTestUtils.getField(draft, "videoStructure"));
        assertEquals(Arrays.asList("水果爱好者", "家庭用户"),
                ReflectionTestUtils.getField(draft, "displayTargetAudienceZh"));
    }

    @Test
    void generateDraftParsesNumericStringsReturnedByAi() throws Exception {
        String aiContent = validAnalysisJson("33")
                .replace("\"count\":3", "\"count\":\"3次\"")
                .replace("\"estimatedConversionRate\":8.9", "\"estimatedConversionRate\":\"8.9%\"");
        Object result = invokeGenerateDraftWithAiContent(aiContent, 33L);
        Object draft = ReflectionTestUtils.getField(result, "draft");
        List<?> sellingPoints = (List<?>) ReflectionTestUtils.getField(draft, "sellingPoints");
        List<?> scriptOptions = (List<?>) ReflectionTestUtils.getField(draft, "scriptOptions");

        assertEquals(3, ReflectionTestUtils.getField(sellingPoints.get(0), "count"));
        assertEquals(new BigDecimal("8.90"),
                ReflectionTestUtils.getField(scriptOptions.get(0), "estimatedConversionRate"));
    }

    @Test
    void generateDraftFallsBackToDisplayPointsZhWhenScriptOptionPointsMissing() throws Exception {
        String aiContent = validAnalysisJson("33")
                .replaceFirst("\"points\":\"[^\"]+\",", "");
        Object result = invokeGenerateDraftWithAiContent(aiContent, 33L);
        Object draft = ReflectionTestUtils.getField(result, "draft");
        List<?> scriptOptions = (List<?>) ReflectionTestUtils.getField(draft, "scriptOptions");

        Object firstOption = scriptOptions.get(0);
        assertEquals(ReflectionTestUtils.getField(firstOption, "displayPointsZh"),
                ReflectionTestUtils.getField(firstOption, "points"));
    }

    @Test
    void generateDraftUsesDefaultEstimatedConversionRateWhenAiReturnsNonNumericText() throws Exception {
        String aiContent = validAnalysisJson("33")
                .replace("\"estimatedConversionRate\":8.9", "\"estimatedConversionRate\":\"高\"");
        Object result = invokeGenerateDraftWithAiContent(aiContent, 33L);
        Object draft = ReflectionTestUtils.getField(result, "draft");
        List<?> scriptOptions = (List<?>) ReflectionTestUtils.getField(draft, "scriptOptions");

        assertEquals(null, ReflectionTestUtils.getField(scriptOptions.get(0), "estimatedConversionRate"));
        assertEquals(null, ReflectionTestUtils.getField(scriptOptions.get(2), "estimatedConversionRate"));
    }

    @Test
    void generateDraftDoesNotPadMissingScriptOptionsWithDuplicateFallbacks() throws Exception {
        Object result = invokeGenerateDraftWithAiContent(validAnalysisJson("33", 3), 33L);
        Object draft = ReflectionTestUtils.getField(result, "draft");
        List<?> scriptOptions = (List<?>) ReflectionTestUtils.getField(draft, "scriptOptions");

        assertEquals(3, scriptOptions.size());
    }

    @Test
    void generateDraftNormalizesIncompleteScriptOptionReturnedByAi() throws Exception {
        String aiContent = validAnalysisJson("33")
                .replaceFirst("\"title\":\"[^\"]+\",", "")
                .replaceFirst("\"points\":\"[^\"]+\",", "")
                .replaceFirst("\"displayTitleZh\":\"[^\"]+\",", "")
                .replaceFirst("\"displayPointsZh\":\"[^\"]+\",", "")
                .replaceFirst("\"conversionLevel\":\"[^\"]+\",", "")
                .replaceFirst("\"scriptText\":\"[^\"]+\",", "")
                .replaceFirst("\"displayScriptZh\":\"[^\"]+\"", "\"displayScriptZh\":\"\"");
        Object result = invokeGenerateDraftWithAiContent(aiContent, 33L);
        Object draft = ReflectionTestUtils.getField(result, "draft");
        List<?> scriptOptions = (List<?>) ReflectionTestUtils.getField(draft, "scriptOptions");

        Object firstOption = scriptOptions.get(0);
        assertTrue(((String) ReflectionTestUtils.getField(firstOption, "title")).length() > 0);
        assertTrue(((String) ReflectionTestUtils.getField(firstOption, "points")).length() > 0);
        assertTrue(((String) ReflectionTestUtils.getField(firstOption, "scriptText")).length() > 0);
        assertEquals("高", ReflectionTestUtils.getField(firstOption, "conversionLevel"));
    }

    @Test
    void extractDomainUsesSupportedUrlInsideShareText() throws Exception {
        TkReferenceAnalysisServiceImpl service = new TkReferenceAnalysisServiceImpl();
        String shareText = "0.76 p@d.AG 02/10 :0pm hOk:/ 不会有人没拍吧 "
                + "https://v.douyin.com/xQ8WdRB7AbQ/ 复制此链接，打开Dou音搜索，直接观看视频！";

        assertEquals("v.douyin.com", invokeExtractDomain(service, shareText));
    }

    @Test
    void extractDomainKeepsTiktokUrlDomain() throws Exception {
        TkReferenceAnalysisServiceImpl service = new TkReferenceAnalysisServiceImpl();

        assertEquals("www.tiktok.com",
                invokeExtractDomain(service, "https://www.tiktok.com/@demo/video/123"));
    }

    private Exception invokeGenerateDraftExpectException(TkReferenceAnalysisServiceImpl service,
                                                        TkReferenceAnalyzeReqVO reqVO,
                                                        TkMaterialLibraryDO library) throws Exception {
        Method method = TkReferenceAnalysisServiceImpl.class
                .getDeclaredMethod("generateDraft", TkReferenceAnalyzeReqVO.class, TkMaterialLibraryDO.class);
        method.setAccessible(true);
        try {
            method.invoke(service, reqVO, library);
            throw new AssertionError("generateDraft should fail");
        } catch (InvocationTargetException ex) {
            assertTrue(ex.getCause().getMessage().contains("Read timed out"));
            return (Exception) ex.getCause();
        }
    }

    private TkReferenceAnalysisDO invokeSaveFailedAnalysis(TkReferenceAnalysisServiceImpl service,
                                                           String businessTraceId,
                                                           TkReferenceAnalyzeReqVO reqVO,
                                                           TkMaterialLibraryDO library,
                                                           Long companyId,
                                                           Long tenantId,
                                                           Exception ex) throws Exception {
        Method method = TkReferenceAnalysisServiceImpl.class
                .getDeclaredMethod("saveFailedAnalysis", String.class, TkReferenceAnalyzeReqVO.class,
                        TkMaterialLibraryDO.class, Long.class, Long.class, Exception.class);
        method.setAccessible(true);
        return (TkReferenceAnalysisDO) method.invoke(service, businessTraceId, reqVO, library, companyId, tenantId, ex);
    }

    private String invokeExtractDomain(TkReferenceAnalysisServiceImpl service, String sourceUrl) throws Exception {
        Method method = TkReferenceAnalysisServiceImpl.class.getDeclaredMethod("extractDomain", String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, sourceUrl);
    }

    private Object invokeGenerateDraftWithAiContent(String aiContent, Long durationSeconds) throws Exception {
        TkReferenceAnalysisServiceImpl service = new TkReferenceAnalysisServiceImpl();
        ReflectionTestUtils.setField(service, "apiKeyConfigService", new DefaultConfigService());
        ReflectionTestUtils.setField(service, "referenceVideoContentService", new FixedVideoContentService(durationSeconds));
        ReflectionTestUtils.setField(service, "referenceAiAnalysisRouter", new TkReferenceAiAnalysisRouter(
                Collections.singletonList(new TkReferenceAiAnalysisClient() {
                    @Override
                    public String provider() {
                        return TkReferenceAnalysisProvider.GEMINI;
                    }

                    @Override
                    public TkReferenceAiAnalysisResult analyze(TkReferenceAiAnalysisContext context) {
                        return new TkReferenceAiAnalysisResult(TkReferenceAnalysisProvider.GEMINI, "gemini-test", aiContent);
                    }
                })));
        TkReferenceAnalyzeReqVO reqVO = new TkReferenceAnalyzeReqVO();
        reqVO.setSourceUrl("https://www.tiktok.com/@demo/video/1");
        reqVO.setTargetLanguage("zh-cn");
        reqVO.setAnalysisProvider(TkReferenceAnalysisProvider.GEMINI);
        TkMaterialLibraryDO library = TkMaterialLibraryDO.builder()
                .id(10L)
                .name("水蜜桃")
                .build();
        Method method = TkReferenceAnalysisServiceImpl.class
                .getDeclaredMethod("generateDraft", TkReferenceAnalyzeReqVO.class, TkMaterialLibraryDO.class);
        method.setAccessible(true);
        return method.invoke(service, reqVO, library);
    }

    private String validAnalysisJson(String videoDurationValue) {
        return validAnalysisJson(videoDurationValue, 12);
    }

    private String validAnalysisJson(String videoDurationValue, int scriptOptionCount) {
        StringBuilder options = new StringBuilder();
        for (int i = 1; i <= scriptOptionCount; i++) {
            if (i > 1) {
                options.append(",");
            }
            options.append("{\"title\":\"标题").append(i)
                    .append("\",\"points\":\"卖点A｜卖点B\",\"displayTitleZh\":\"标题")
                    .append(i)
                    .append("\",\"displayPointsZh\":\"卖点A｜卖点B\",\"estimatedConversionRate\":8.9,")
                    .append("\"conversionLevel\":\"高\",\"segmentTimeline\":[{\"timeWindow\":\"0-3s\",")
                    .append("\"segmentLibrary\":\"S1_HOOK\",\"scriptLine\":\"口播原文\",")
                    .append("\"displayScriptLineZh\":\"口播原文\",\"visualDirection\":\"展示商品\"}],")
                    .append("\"scriptText\":\"口播原文\",\"displayScriptZh\":\"口播原文\"}");
        }
        return "{"
                + "\"productName\":\"水蜜桃\","
                + "\"videoDuration\":" + videoDurationValue + ","
                + "\"publishTime\":\"\","
                + "\"coreSellingPoints\":[\"爆汁\",\"现摘\",\"桃香\"],"
                + "\"targetAudience\":[\"爱吃水果的人\"],"
                + "\"usageScenarios\":[\"家庭分享\"],"
                + "\"videoStructure\":[\"开头\",\"展示\",\"结尾\"],"
                + "\"displayProductNameZh\":\"水蜜桃\","
                + "\"displayCoreSellingPointsZh\":[\"爆汁\",\"现摘\",\"桃香\"],"
                + "\"displayTargetAudienceZh\":[\"爱吃水果的人\"],"
                + "\"displayUsageScenariosZh\":[\"家庭分享\"],"
                + "\"displayVideoStructureZh\":[\"开头\",\"展示\",\"结尾\"],"
                + "\"sellingPoints\":[{\"title\":\"爆汁\",\"desc\":\"一口爆汁\",\"count\":3,\"badge\":\"核心卖点\"}],"
                + "\"displaySellingPointsZh\":[{\"title\":\"爆汁\",\"desc\":\"一口爆汁\",\"count\":3,\"badge\":\"核心卖点\"}],"
                + "\"scriptOptions\":[" + options + "]"
                + "}";
    }

    private static class TimeoutGeminiClient extends TkGeminiClient {
        @Override
        public String generateText(String prompt, List<TkAiImageInput> images) {
            throw new IllegalStateException("Read timed out");
        }
    }

    private static class DefaultConfigService implements TkApiKeyConfigService {

        @Override
        public String getValue(String provider, String configKey) {
            return null;
        }

        @Override
        public String getValueOrDefault(String provider, String configKey, String defaultValue) {
            assertEquals(TkApiKeyProviderEnum.GEMINI.getProvider(), provider);
            return defaultValue;
        }
    }

    private static class ParsedVideoContentService implements TkReferenceVideoContentService {
        @Override
        public TkReferenceVideoContent analyze(String sourceUrl) {
            return analyze(sourceUrl, null);
        }

        @Override
        public TkReferenceVideoContent analyze(String sourceUrl, Long libraryId) {
            return new TkReferenceVideoContent(sourceUrl, "https://example.com/reference.mp4",
                    "https://example.com/reference.jpg", 25L, "1080x1920",
                    Collections.singletonList(new TkReferenceVideoContent.Frame(1, "image/jpeg", "ZmFrZQ==")));
        }

        @Override
        public TkReferenceOpeningClip createOpeningClip(String sourceUrl, Integer startSecond, Integer endSecond,
                                                        Long tenantId, Long companyId) {
            return null;
        }
    }

    private static class FixedVideoContentService implements TkReferenceVideoContentService {

        private final Long durationSeconds;

        private FixedVideoContentService(Long durationSeconds) {
            this.durationSeconds = durationSeconds;
        }

        @Override
        public TkReferenceVideoContent analyze(String sourceUrl) {
            return analyze(sourceUrl, null);
        }

        @Override
        public TkReferenceVideoContent analyze(String sourceUrl, Long libraryId) {
            return new TkReferenceVideoContent(sourceUrl, "https://example.com/reference.mp4",
                    "https://example.com/reference.jpg", durationSeconds, "720x1280",
                    Collections.singletonList(new TkReferenceVideoContent.Frame(0, "image/jpeg", "ZmFrZQ==")));
        }

        @Override
        public TkReferenceOpeningClip createOpeningClip(String sourceUrl, Integer startSecond, Integer endSecond,
                                                        Long tenantId, Long companyId) {
            return null;
        }
    }

    private static class CapturingExecutorService extends AbstractExecutorService {

        private int submittedCount;
        private boolean shutdown;

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            return Collections.emptyList();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return shutdown;
        }

        @Override
        public void execute(Runnable command) {
            submittedCount++;
        }

        int getSubmittedCount() {
            return submittedCount;
        }
    }
}

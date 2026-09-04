package cn.iocoder.yudao.module.tk.service.tiktok;

import cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo.TkTiktokPublishUrlRegisterReqVO;
import cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo.TkTiktokPublishCreateReqVO;
import cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo.TkTiktokPublishUrlRespVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationTaskDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkTiktokAccountDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkTiktokPublishDetailDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkTiktokPublishTaskDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkTiktokPublishDetailMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.TkTiktokPublishTaskMapper;
import cn.iocoder.yudao.module.tk.service.generation.TkGenerationTaskService;
import cn.iocoder.yudao.module.tk.service.log.TkBusinessLogService;
import cn.iocoder.yudao.module.tk.service.scope.TkDataScopeService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.sun.net.httpserver.HttpServer;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TkTiktokPublishServiceImplTest {

    @Test
    void creatorInfoRetriesOnceAfterExactAccessTokenInvalid() {
        TkTiktokApiClient apiClient = mock(TkTiktokApiClient.class);
        TkTiktokTokenService tokenService = mock(TkTiktokTokenService.class);
        TkTiktokPublishServiceImpl service = createRetryService(apiClient, tokenService);
        TkTiktokApiClient.CreatorInfo invalid = new TkTiktokApiClient.CreatorInfo(false,
                "access_token_invalid：expired，log_id=creator-old", Collections.emptyList(),
                false, false, false, null, "access_token_invalid");
        TkTiktokApiClient.CreatorInfo success = new TkTiktokApiClient.CreatorInfo(true,
                null, Collections.singletonList("SELF_ONLY"), false, false, false, 300, null);
        when(tokenService.getValidAccessToken(10L)).thenReturn("access-old");
        when(tokenService.forceRefreshAccessToken(10L)).thenReturn("access-new");
        when(apiClient.queryCreatorInfo("access-old")).thenReturn(invalid);
        when(apiClient.queryCreatorInfo("access-new")).thenReturn(success);

        TkTiktokApiClient.CreatorInfo result = service.queryCreatorInfoWithRetry(10L);

        assertTrue(result.isSuccess());
        verify(apiClient).queryCreatorInfo("access-old");
        verify(apiClient).queryCreatorInfo("access-new");
        verify(tokenService).forceRefreshAccessToken(10L);
    }

    @Test
    void creatorInfoDoesNotRetryNonTokenError() {
        TkTiktokApiClient apiClient = mock(TkTiktokApiClient.class);
        TkTiktokTokenService tokenService = mock(TkTiktokTokenService.class);
        TkTiktokPublishServiceImpl service = createRetryService(apiClient, tokenService);
        TkTiktokApiClient.CreatorInfo denied = new TkTiktokApiClient.CreatorInfo(false,
                "scope_not_authorized：missing scope，log_id=scope-log", Collections.emptyList(),
                false, false, false, null, "scope_not_authorized");
        when(tokenService.getValidAccessToken(11L)).thenReturn("access-current");
        when(apiClient.queryCreatorInfo("access-current")).thenReturn(denied);

        TkTiktokApiClient.CreatorInfo result = service.queryCreatorInfoWithRetry(11L);

        assertEquals("scope_not_authorized", result.getErrorCode());
        verify(tokenService, never()).forceRefreshAccessToken(11L);
        verify(apiClient, times(1)).queryCreatorInfo("access-current");
    }

    @Test
    void publishInitRetriesOnceAfterExactAccessTokenInvalid() {
        TkTiktokApiClient apiClient = mock(TkTiktokApiClient.class);
        TkTiktokTokenService tokenService = mock(TkTiktokTokenService.class);
        TkTiktokPublishServiceImpl service = createRetryService(apiClient, tokenService);
        TkTiktokApiClient.PublishResult invalid = new TkTiktokApiClient.PublishResult(false,
                null, null, "access_token_invalid：expired，log_id=publish-old", "access_token_invalid");
        TkTiktokApiClient.PublishResult success = new TkTiktokApiClient.PublishResult(true,
                "publish-1", "https://upload.example/video", null, null);
        HashMap<String, Object> payload = new HashMap<>();
        when(tokenService.getValidAccessToken(12L)).thenReturn("access-old");
        when(tokenService.forceRefreshAccessToken(12L)).thenReturn("access-new");
        when(apiClient.initVideoPost("access-old", "DIRECT_POST", payload)).thenReturn(invalid);
        when(apiClient.initVideoPost("access-new", "DIRECT_POST", payload)).thenReturn(success);

        TkTiktokApiClient.PublishResult result = service.initVideoPostWithRetry(12L, "DIRECT_POST", payload);

        assertEquals("publish-1", result.getPublishId());
        verify(apiClient).initVideoPost("access-old", "DIRECT_POST", payload);
        verify(apiClient).initVideoPost("access-new", "DIRECT_POST", payload);
        verify(tokenService).forceRefreshAccessToken(12L);
    }

    @Test
    void postStatusRetriesOnceAndStopsWhenRefreshIsUnrecoverable() {
        TkTiktokApiClient apiClient = mock(TkTiktokApiClient.class);
        TkTiktokTokenService tokenService = mock(TkTiktokTokenService.class);
        TkTiktokPublishServiceImpl service = createRetryService(apiClient, tokenService);
        TkTiktokApiClient.PostStatusResult invalid = new TkTiktokApiClient.PostStatusResult(false,
                "FAILED", "access_token_invalid：expired，log_id=status-old", "access_token_invalid");
        when(tokenService.getValidAccessToken(13L)).thenReturn("access-old");
        when(tokenService.forceRefreshAccessToken(13L))
                .thenThrow(new IllegalStateException("Refresh Token 已过期，请重新授权账号"));
        when(apiClient.fetchPostStatus("access-old", "publish-13")).thenReturn(invalid);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.fetchPostStatusWithRetry(13L, "publish-13"));

        assertTrue(error.getMessage().contains("重新授权"));
        verify(apiClient, times(1)).fetchPostStatus("access-old", "publish-13");
        verify(tokenService, times(1)).forceRefreshAccessToken(13L);
    }

    @Test
    void uploadFailureRetainsPublishIdForAutomaticStatusReconciliation() throws Exception {
        HttpServer videoServer = HttpServer.create(new InetSocketAddress(0), 0);
        byte[] videoBytes = "video".getBytes(StandardCharsets.UTF_8);
        videoServer.createContext("/video.mp4", exchange -> {
            exchange.sendResponseHeaders(200, videoBytes.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(videoBytes);
            }
        });
        videoServer.start();

        try {
            TkTiktokApiClient apiClient = mock(TkTiktokApiClient.class);
            TkTiktokTokenService tokenService = mock(TkTiktokTokenService.class);
            cn.iocoder.yudao.module.tk.dal.mysql.TkTiktokAccountMapper accountMapper =
                    mock(cn.iocoder.yudao.module.tk.dal.mysql.TkTiktokAccountMapper.class);
            TkTiktokPublishTaskMapper taskMapper = mock(TkTiktokPublishTaskMapper.class);
            TkTiktokPublishDetailMapper detailMapper = mock(TkTiktokPublishDetailMapper.class);
            TkBusinessLogService businessLogService = mock(TkBusinessLogService.class);
            TkTiktokPublishServiceImpl service = new TkTiktokPublishServiceImpl();
            ReflectionTestUtils.setField(service, "apiClient", apiClient);
            ReflectionTestUtils.setField(service, "tokenService", tokenService);
            ReflectionTestUtils.setField(service, "accountMapper", accountMapper);
            ReflectionTestUtils.setField(service, "publishTaskMapper", taskMapper);
            ReflectionTestUtils.setField(service, "publishDetailMapper", detailMapper);
            ReflectionTestUtils.setField(service, "businessLogService", businessLogService);

            TkTiktokAccountDO account = TkTiktokAccountDO.builder()
                    .id(1L)
                    .authStatus("AUTHORIZED")
                    .displayName("demo")
                    .build();
            TkTiktokPublishTaskDO task = TkTiktokPublishTaskDO.builder()
                    .id(2L)
                    .videoUrl("http://localhost:" + videoServer.getAddress().getPort() + "/video.mp4")
                    .title("Demo")
                    .caption("Caption")
                    .build();
            TkTiktokPublishDetailDO detail = TkTiktokPublishDetailDO.builder()
                    .id(3L)
                    .accountId(1L)
                    .publishTaskId(2L)
                    .accountDisplayName("demo")
                    .status("PENDING")
                    .tiktokStatus("LOCAL_PENDING")
                    .postMode("DIRECT_POST")
                    .allowComment(true)
                    .allowDuet(false)
                    .allowStitch(false)
                    .brandContent(false)
                    .commercialContent(false)
                    .aigcContent(true)
                    .build();

            when(accountMapper.selectById(1L)).thenReturn(account);
            when(taskMapper.selectById(2L)).thenReturn(task);
            when(detailMapper.update(any(), any())).thenReturn(1);
            when(apiClient.isConfigured()).thenReturn(true);
            when(apiClient.getVerifiedPullDomain()).thenReturn("");
            when(tokenService.getValidAccessToken(1L)).thenReturn("access-token");
            when(apiClient.queryCreatorInfo("access-token")).thenReturn(new TkTiktokApiClient.CreatorInfo(
                    true, null, Collections.singletonList("SELF_ONLY"), false, false, false, 300, null));
            when(apiClient.initVideoPost(any(), any(), anyMap())).thenReturn(new TkTiktokApiClient.PublishResult(
                    true, "publish-1", "https://upload.example/video", null, null));
            doThrow(new TkTiktokApiClient.UploadException(403))
                    .when(apiClient).uploadVideoChunks(anyString(), any(Path.class), anyString());

            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                    TkTiktokPublishDetailDO.class);
            ReflectionTestUtils.invokeMethod(service, "processDetail", detail);

            assertEquals("publish-1", detail.getPublishId());
            assertEquals("PROCESSING", detail.getStatus());
            assertEquals("UPLOAD_PENDING", detail.getTiktokStatus());
            assertTrue(detail.getFailReason().contains("HTTP 403"));
        } finally {
            videoServer.stop(0);
        }
    }

    @Test
    void uploadPendingStatusErrorRemainsProcessingForAutomaticRetry() {
        TkTiktokApiClient apiClient = mock(TkTiktokApiClient.class);
        TkTiktokTokenService tokenService = mock(TkTiktokTokenService.class);
        cn.iocoder.yudao.module.tk.dal.mysql.TkTiktokAccountMapper accountMapper =
                mock(cn.iocoder.yudao.module.tk.dal.mysql.TkTiktokAccountMapper.class);
        TkTiktokPublishDetailMapper detailMapper = mock(TkTiktokPublishDetailMapper.class);
        TkBusinessLogService businessLogService = mock(TkBusinessLogService.class);
        TkTiktokPublishServiceImpl service = new TkTiktokPublishServiceImpl();
        ReflectionTestUtils.setField(service, "apiClient", apiClient);
        ReflectionTestUtils.setField(service, "tokenService", tokenService);
        ReflectionTestUtils.setField(service, "accountMapper", accountMapper);
        ReflectionTestUtils.setField(service, "publishDetailMapper", detailMapper);
        ReflectionTestUtils.setField(service, "businessLogService", businessLogService);

        TkTiktokAccountDO account = TkTiktokAccountDO.builder()
                .id(1L)
                .authStatus("AUTHORIZED")
                .build();
        TkTiktokPublishDetailDO detail = TkTiktokPublishDetailDO.builder()
                .id(3L)
                .accountId(1L)
                .publishId("publish-1")
                .status("PROCESSING")
                .tiktokStatus("UPLOAD_PENDING")
                .build();
        when(accountMapper.selectById(1L)).thenReturn(account);
        when(tokenService.getValidAccessToken(1L)).thenReturn("access-token");
        when(apiClient.fetchPostStatus("access-token", "publish-1")).thenReturn(
                new TkTiktokApiClient.PostStatusResult(false, "FAILED", "publish_id 尚未可查询", "not_found"));

        ReflectionTestUtils.invokeMethod(service, "syncProcessingDetail", detail);

        assertEquals("PROCESSING", detail.getStatus());
        assertEquals("UPLOAD_PENDING", detail.getTiktokStatus());
        assertEquals("publish_id 尚未可查询", detail.getFailReason());
    }

    @Test
    void successfulPublicPostAutomaticallyPersistsShareUrl() {
        TkTiktokApiClient apiClient = mock(TkTiktokApiClient.class);
        TkTiktokTokenService tokenService = mock(TkTiktokTokenService.class);
        cn.iocoder.yudao.module.tk.dal.mysql.TkTiktokAccountMapper accountMapper =
                mock(cn.iocoder.yudao.module.tk.dal.mysql.TkTiktokAccountMapper.class);
        TkTiktokPublishDetailMapper detailMapper = mock(TkTiktokPublishDetailMapper.class);
        TkBusinessLogService businessLogService = mock(TkBusinessLogService.class);
        TkTiktokPublishServiceImpl service = new TkTiktokPublishServiceImpl();
        ReflectionTestUtils.setField(service, "apiClient", apiClient);
        ReflectionTestUtils.setField(service, "tokenService", tokenService);
        ReflectionTestUtils.setField(service, "accountMapper", accountMapper);
        ReflectionTestUtils.setField(service, "publishDetailMapper", detailMapper);
        ReflectionTestUtils.setField(service, "businessLogService", businessLogService);

        TkTiktokAccountDO account = TkTiktokAccountDO.builder()
                .id(1L)
                .authStatus("AUTHORIZED")
                .build();
        TkTiktokPublishDetailDO detail = TkTiktokPublishDetailDO.builder()
                .id(3L)
                .accountId(1L)
                .publishId("publish-1")
                .status("PROCESSING")
                .tiktokStatus("PROCESSING")
                .build();
        when(accountMapper.selectById(1L)).thenReturn(account);
        when(tokenService.getValidAccessToken(1L)).thenReturn("access-token");
        when(apiClient.fetchPostStatus("access-token", "publish-1")).thenReturn(
                new TkTiktokApiClient.PostStatusResult(true, "PUBLISH_COMPLETE", null, null,
                        Collections.singletonList("post-1")));
        when(apiClient.queryVideoShareUrl("access-token", Collections.singletonList("post-1"))).thenReturn(
                new TkTiktokApiClient.VideoQueryResult(true,
                        "https://www.tiktok.com/@demo/video/post-1", null, null));
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                TkTiktokPublishDetailDO.class);

        ReflectionTestUtils.invokeMethod(service, "syncProcessingDetail", detail);

        assertEquals("SUCCESS", detail.getStatus());
        assertEquals("PUBLISH_COMPLETE", detail.getTiktokStatus());
        assertEquals("https://www.tiktok.com/@demo/video/post-1", detail.getPublishUrl());
        assertNotNull(detail.getPublishUrlRegisteredTime());
        verify(apiClient).queryVideoShareUrl("access-token", Collections.singletonList("post-1"));
        verify(detailMapper).update(any(), any());
    }

    @Test
    void publicPostWaitsForPublicIdBeforeMarkingPublishSuccessful() {
        TkTiktokApiClient apiClient = mock(TkTiktokApiClient.class);
        TkTiktokTokenService tokenService = mock(TkTiktokTokenService.class);
        cn.iocoder.yudao.module.tk.dal.mysql.TkTiktokAccountMapper accountMapper =
                mock(cn.iocoder.yudao.module.tk.dal.mysql.TkTiktokAccountMapper.class);
        TkTiktokPublishDetailMapper detailMapper = mock(TkTiktokPublishDetailMapper.class);
        TkBusinessLogService businessLogService = mock(TkBusinessLogService.class);
        TkTiktokPublishServiceImpl service = new TkTiktokPublishServiceImpl();
        ReflectionTestUtils.setField(service, "apiClient", apiClient);
        ReflectionTestUtils.setField(service, "tokenService", tokenService);
        ReflectionTestUtils.setField(service, "accountMapper", accountMapper);
        ReflectionTestUtils.setField(service, "publishDetailMapper", detailMapper);
        ReflectionTestUtils.setField(service, "businessLogService", businessLogService);

        TkTiktokAccountDO account = TkTiktokAccountDO.builder()
                .id(1L)
                .authStatus("AUTHORIZED")
                .build();
        TkTiktokPublishDetailDO detail = TkTiktokPublishDetailDO.builder()
                .id(3L)
                .accountId(1L)
                .publishId("publish-1")
                .privacyLevel("PUBLIC_TO_EVERYONE")
                .status("PROCESSING")
                .tiktokStatus("PROCESSING")
                .build();
        when(accountMapper.selectById(1L)).thenReturn(account);
        when(tokenService.getValidAccessToken(1L)).thenReturn("access-token");
        when(apiClient.fetchPostStatus("access-token", "publish-1")).thenReturn(
                new TkTiktokApiClient.PostStatusResult(true, "PUBLISH_COMPLETE", null, null,
                        Collections.emptyList()));
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                TkTiktokPublishDetailDO.class);

        ReflectionTestUtils.invokeMethod(service, "syncProcessingDetail", detail);

        assertEquals("PROCESSING", detail.getStatus());
        assertEquals("PUBLISH_COMPLETE", detail.getTiktokStatus());
        verify(detailMapper).updateById(detail);
        verify(apiClient, never()).queryVideoShareUrl(anyString(), any());
    }

    @Test
    void retryClearsPreviousPublishUrlBeforeNewAttempt() {
        TkTiktokPublishTaskMapper taskMapper = mock(TkTiktokPublishTaskMapper.class);
        TkTiktokPublishDetailMapper detailMapper = mock(TkTiktokPublishDetailMapper.class);
        TkDataScopeService dataScopeService = mock(TkDataScopeService.class);
        TkTiktokPublishServiceImpl service = createService(taskMapper, detailMapper, null, dataScopeService);
        TkTiktokPublishDetailDO detail = TkTiktokPublishDetailDO.builder()
                .id(30L)
                .companyId(20L)
                .publishTaskId(31L)
                .status("FAILED")
                .publishUrl("https://www.tiktok.com/@demo/video/old")
                .publishUrlRegisteredTime(java.time.LocalDateTime.now())
                .retryCount(1)
                .build();
        detail.setTenantId(8L);
        when(detailMapper.selectById(30L)).thenReturn(detail);
        when(taskMapper.selectById(31L)).thenReturn(null);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                TkTiktokPublishDetailDO.class);

        try {
            TransactionSynchronizationManager.setActualTransactionActive(true);
            TransactionSynchronizationManager.initSynchronization();
            service.retry(30L);
        } finally {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.clearSynchronization();
            }
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }

        assertNull(detail.getPublishUrl());
        assertNull(detail.getPublishUrlRegisteredTime());
        assertEquals("PENDING", detail.getStatus());
        verify(detailMapper).update(any(), any());
    }

    @Test
    void validateVideoSourceRejectsMissingOrAmbiguousSource() {
        TkTiktokPublishCreateReqVO missing = new TkTiktokPublishCreateReqVO();
        assertThrows(IllegalArgumentException.class, () -> TkTiktokPublishServiceImpl.validateVideoSource(missing));

        TkTiktokPublishCreateReqVO both = new TkTiktokPublishCreateReqVO();
        both.setGenerationTaskId(1L);
        both.setUploadedVideoId(2L);
        assertThrows(IllegalArgumentException.class, () -> TkTiktokPublishServiceImpl.validateVideoSource(both));
    }

    @Test
    void registerPublishUrlUpdatesLatestDetailForGenerationTask() {
        TkTiktokPublishDetailMapper detailMapper = mock(TkTiktokPublishDetailMapper.class);
        TkGenerationTaskService generationTaskService = mock(TkGenerationTaskService.class);
        TkDataScopeService dataScopeService = mock(TkDataScopeService.class);
        TkTiktokPublishServiceImpl service = createService(null, detailMapper, generationTaskService, dataScopeService);
        TkGenerationTaskDO generationTask = TkGenerationTaskDO.builder()
                .id(100L)
                .companyId(20L)
                .status("SUCCESS")
                .outputUrl("https://oss.example.com/video.mp4")
                .build();
        generationTask.setTenantId(8L);
        TkTiktokPublishDetailDO latestDetail = TkTiktokPublishDetailDO.builder()
                .id(300L)
                .generationTaskId(100L)
                .companyId(20L)
                .accountDisplayName("demo")
                .status("SUCCESS")
                .build();
        latestDetail.setTenantId(8L);
        when(generationTaskService.getGenerationTask(100L)).thenReturn(generationTask);
        when(detailMapper.selectLatestRegisteredTargetByGenerationTaskId(100L)).thenReturn(latestDetail);
        TkTiktokPublishUrlRegisterReqVO reqVO = new TkTiktokPublishUrlRegisterReqVO();
        reqVO.setGenerationTaskId(100L);
        reqVO.setPublishUrl("  https://www.tiktok.com/@demo/video/123  ");

        TkTiktokPublishUrlRespVO result = service.registerPublishUrl(reqVO);

        verify(dataScopeService).validateWritable(8L, 20L);
        ArgumentCaptor<TkTiktokPublishDetailDO> captor = ArgumentCaptor.forClass(TkTiktokPublishDetailDO.class);
        verify(detailMapper).updateById(captor.capture());
        assertEquals(300L, captor.getValue().getId());
        assertEquals("https://www.tiktok.com/@demo/video/123", captor.getValue().getPublishUrl());
        assertNotNull(captor.getValue().getPublishUrlRegisteredTime());
        assertEquals(300L, result.getPublishDetailId());
        assertEquals("https://www.tiktok.com/@demo/video/123", result.getPublishUrl());
    }

    @Test
    void registerPublishUrlCreatesManualDetailWhenGenerationTaskHasNoPublishDetail() {
        TkTiktokPublishTaskMapper taskMapper = mock(TkTiktokPublishTaskMapper.class);
        TkTiktokPublishDetailMapper detailMapper = mock(TkTiktokPublishDetailMapper.class);
        TkGenerationTaskService generationTaskService = mock(TkGenerationTaskService.class);
        TkDataScopeService dataScopeService = mock(TkDataScopeService.class);
        TkTiktokPublishServiceImpl service = createService(taskMapper, detailMapper, generationTaskService, dataScopeService);
        TkGenerationTaskDO generationTask = TkGenerationTaskDO.builder()
                .id(100L)
                .businessTraceId("TRACE-001")
                .companyId(20L)
                .status("SUCCESS")
                .outputUrl("https://oss.example.com/video.mp4")
                .title("Demo video")
                .build();
        generationTask.setTenantId(8L);
        when(generationTaskService.getGenerationTask(100L)).thenReturn(generationTask);
        when(detailMapper.selectLatestRegisteredTargetByGenerationTaskId(100L)).thenReturn(null);
        org.mockito.Mockito.doAnswer(invocation -> {
            TkTiktokPublishTaskDO task = invocation.getArgument(0);
            task.setId(200L);
            return 1;
        }).when(taskMapper).insert(any(TkTiktokPublishTaskDO.class));
        org.mockito.Mockito.doAnswer(invocation -> {
            TkTiktokPublishDetailDO detail = invocation.getArgument(0);
            detail.setId(300L);
            return 1;
        }).when(detailMapper).insert(any(TkTiktokPublishDetailDO.class));
        TkTiktokPublishUrlRegisterReqVO reqVO = new TkTiktokPublishUrlRegisterReqVO();
        reqVO.setGenerationTaskId(100L);
        reqVO.setPublishUrl("https://www.tiktok.com/@demo/video/123");

        TkTiktokPublishUrlRespVO result = service.registerPublishUrl(reqVO);

        verify(dataScopeService).validateWritable(8L, 20L);
        ArgumentCaptor<TkTiktokPublishTaskDO> taskCaptor = ArgumentCaptor.forClass(TkTiktokPublishTaskDO.class);
        verify(taskMapper).insert(taskCaptor.capture());
        assertEquals("MANUAL_REGISTER", taskCaptor.getValue().getPostMode());
        assertEquals("SUCCESS", taskCaptor.getValue().getStatus());
        ArgumentCaptor<TkTiktokPublishDetailDO> detailCaptor = ArgumentCaptor.forClass(TkTiktokPublishDetailDO.class);
        verify(detailMapper).insert(detailCaptor.capture());
        assertEquals(200L, detailCaptor.getValue().getPublishTaskId());
        assertEquals("MANUAL_REGISTER", detailCaptor.getValue().getPostMode());
        assertEquals("SUCCESS", detailCaptor.getValue().getStatus());
        assertEquals("https://www.tiktok.com/@demo/video/123", detailCaptor.getValue().getPublishUrl());
        assertEquals(300L, result.getPublishDetailId());
    }

    private TkTiktokPublishServiceImpl createService(TkTiktokPublishTaskMapper taskMapper,
                                                     TkTiktokPublishDetailMapper detailMapper,
                                                     TkGenerationTaskService generationTaskService,
                                                     TkDataScopeService dataScopeService) {
        TkTiktokPublishServiceImpl service = new TkTiktokPublishServiceImpl();
        ReflectionTestUtils.setField(service, "publishTaskMapper", taskMapper);
        ReflectionTestUtils.setField(service, "publishDetailMapper", detailMapper);
        ReflectionTestUtils.setField(service, "generationTaskService", generationTaskService);
        ReflectionTestUtils.setField(service, "dataScopeService", dataScopeService);
        return service;
    }

    private TkTiktokPublishServiceImpl createRetryService(TkTiktokApiClient apiClient,
                                                          TkTiktokTokenService tokenService) {
        TkTiktokPublishServiceImpl service = new TkTiktokPublishServiceImpl();
        ReflectionTestUtils.setField(service, "apiClient", apiClient);
        ReflectionTestUtils.setField(service, "tokenService", tokenService);
        return service;
    }
}

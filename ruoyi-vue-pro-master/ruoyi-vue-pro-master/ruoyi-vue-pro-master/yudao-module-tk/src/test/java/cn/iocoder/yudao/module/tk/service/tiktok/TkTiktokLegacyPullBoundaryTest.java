package cn.iocoder.yudao.module.tk.service.tiktok;

import cn.iocoder.yudao.module.tk.dal.dataobject.*;
import cn.iocoder.yudao.module.tk.dal.mysql.*;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiSecretCipher;
import cn.iocoder.yudao.module.tk.service.log.TkBusinessLogService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TkTiktokLegacyPullBoundaryTest {
    @Test
    void protectedPendingSnapshotsNeverInitializeAgain() {
        for (String phase : new String[]{"INIT_IN_FLIGHT", "RECOVERY_REQUIRED"}) {
            Fixture f = new Fixture();
            f.detail.setInitPhase(phase);
            ReflectionTestUtils.invokeMethod(f.service, "processDetail", f.detail);
            verifyNoInteractions(f.api, f.details);
            f.service.destroy();
        }
        Fixture f = new Fixture();
        f.detail.setPublishId("official-id");
        ReflectionTestUtils.invokeMethod(f.service, "processDetail", f.detail);
        verifyNoInteractions(f.api, f.details);
        f.service.destroy();
    }

    @Test
    void pullInitTimeoutStaysProcessingAndCannotReinitialize() {
        Fixture f = new Fixture();
        when(f.api.initVideoPost(any(), any(), anyMap())).thenThrow(new IllegalStateException("timeout"));
        ReflectionTestUtils.invokeMethod(f.service, "processDetail", f.detail);
        assertEquals("PROCESSING", f.detail.getStatus());
        assertEquals("RECOVERY_REQUIRED", f.detail.getInitPhase());
        f.detail.setStatus("PENDING");
        ReflectionTestUtils.invokeMethod(f.service, "processDetail", f.detail);
        verify(f.api, times(1)).initVideoPost(any(), any(), anyMap());
        verify(f.api, never()).uploadVideoChunks(any(), any(), any());
        f.service.destroy();
    }

    @Test
    void pullSuccessUsesMappedUrlAndPersistsOfficialIdWithoutChunks() {
        Fixture f = new Fixture();
        when(f.api.initVideoPost(any(), any(), anyMap())).thenAnswer(call -> {
            java.util.Map<?, ?> payload = call.getArgument(2);
            java.util.Map<?, ?> source = (java.util.Map<?, ?>) payload.get("source_info");
            assertEquals("https://tkpublish.fnn.net.cn/video.mp4", source.get("video_url"));
            assertFalse(source.containsKey("video_size"));
            return new TkTiktokApiClient.PublishResult(true, "official-id", null, null, null);
        });
        ReflectionTestUtils.invokeMethod(f.service, "processDetail", f.detail);
        assertEquals("official-id", f.detail.getPublishId());
        assertEquals("INIT_CONFIRMED", f.detail.getInitPhase());
        assertEquals("PROCESSING_DOWNLOAD", f.detail.getTiktokStatus());
        assertEquals(300L, f.detail.getSourceFileSize());
        verify(f.api, never()).uploadVideoChunks(any(), any(), any());
        f.service.destroy();
    }

    @Test
    void transientStatusFailurePreservesPullProcessing() {
        Fixture f = new Fixture();
        f.detail.setStatus("PROCESSING");
        f.detail.setPublishId("official-id");
        f.detail.setTransferSource("PULL_FROM_URL");
        f.detail.setTiktokStatus("PROCESSING_DOWNLOAD");
        when(f.api.fetchPostStatusOnce(any(), any())).thenThrow(new IllegalStateException("timeout"));
        ReflectionTestUtils.invokeMethod(f.service, "syncProcessingDetail", f.detail);
        assertEquals("PROCESSING", f.detail.getStatus());
        assertEquals("PROCESSING_DOWNLOAD", f.detail.getTiktokStatus());
        assertNotNull(f.detail.getNextPollTime());
        f.service.destroy();
    }

    @Test
    void knownPublishIdWithMissingAccountRemainsRecoverable() {
        Fixture f = new Fixture();
        f.detail.setStatus("PROCESSING");
        f.detail.setPublishId("official-id");
        TkTiktokAccountMapper accounts = (TkTiktokAccountMapper) ReflectionTestUtils.getField(f.service, "accountMapper");
        when(accounts.selectById(1L)).thenReturn(null);
        ReflectionTestUtils.invokeMethod(f.service, "syncProcessingDetail", f.detail);
        assertEquals("PROCESSING", f.detail.getStatus());
        assertEquals("RECOVERY_REQUIRED", f.detail.getInitPhase());
        assertNotNull(f.detail.getNextPollTime());
        verify(f.api, never()).fetchPostStatusOnce(any(), any());
        f.service.destroy();
    }

    @Test
    void fourArgumentFilePolicyNeverUsesHistoricalVerifiedUrlShortcut() throws Exception {
        Fixture f = new Fixture();
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress(0), 0);
        server.createContext("/video.mp4", exchange -> {
            byte[] body = new byte[300];
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            String url = "http://localhost:" + server.getAddress().getPort() + "/video.mp4";
            Object source = ReflectionTestUtils.invokeMethod(f.service, "resolveUploadSource", url, "video/mp4", 1L, false);
            assertEquals(false, ReflectionTestUtils.getField(source, "pullFromUrl"));
            assertEquals(300L, ReflectionTestUtils.getField(source, "videoSize"));
            ReflectionTestUtils.invokeMethod(source, "cleanup");
        } finally {
            server.stop(0);
            f.service.destroy();
        }
    }

    @Test
    void resultWriteIncludesAttemptVersionAndOfficialIdFence() {
        Fixture f = new Fixture();
        f.detail.setStatus("PROCESSING");
        f.detail.setPublishId("official-id");
        f.detail.setRetryCount(7);
        ReflectionTestUtils.invokeMethod(f.service, "markStatusPending", f.detail);
        org.mockito.ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.Wrapper<TkTiktokPublishDetailDO>> wrapper =
                org.mockito.ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);
        verify(f.details).update(eq(f.detail), wrapper.capture());
        String sql = wrapper.getValue().getSqlSegment();
        assertTrue(sql.contains("retry_count"));
        assertTrue(sql.contains("publish_id"));
        java.util.Collection<?> values = ((com.baomidou.mybatisplus.core.conditions.AbstractWrapper<?, ?, ?>) wrapper.getValue()).getParamNameValuePairs().values();
        assertTrue(values.contains(7));
        assertTrue(values.contains("official-id"));
        f.service.destroy();
    }

    @Test
    void scheduledPullCandidateRetainsOriginalUrlWithoutLocalPersistence() {
        assertScheduledPolicy(true, "OSS_PULL_V1");
    }

    @Test
    void newIneligibleScheduledTaskIsForcedFileRatherThanHistoricalLegacy() {
        assertScheduledPolicy(false, "FILE_UPLOAD");
    }

    @Test
    void privateSignedScheduledCandidatePersistsBeforeTheSignatureCanExpire() {
        assertScheduledPolicy(true, false, "FILE_UPLOAD");
    }

    private void assertScheduledPolicy(boolean candidate, String expectedPolicy) {
        assertScheduledPolicy(candidate, candidate, expectedPolicy);
    }

    private void assertScheduledPolicy(boolean candidate, boolean anonymousAccessible, String expectedPolicy) {
        Fixture f = new Fixture();
        TkTiktokScheduledMediaService scheduled = mock(TkTiktokScheduledMediaService.class);
        ReflectionTestUtils.setField(f.service, "scheduledMediaService", scheduled);
        TkTiktokPublishTaskMapper tasks = (TkTiktokPublishTaskMapper) ReflectionTestUtils.getField(f.service, "publishTaskMapper");
        TkTiktokPullSourceResolver resolver = (TkTiktokPullSourceResolver) ReflectionTestUtils.getField(f.service, "pullSourceResolver");
        String original = "https://tkpublish.fnn.net.cn/video.mp4?OSSAccessKeyId=test&Expires=1&Signature=test";
        when(resolver.isCandidate(eq(original), eq(1L), anyString())).thenReturn(candidate);
        when(resolver.resolve(eq(original), eq(1L), anyString())).thenReturn(anonymousAccessible
                ? TkTiktokPullSourceResolver.PullDecision.eligible("https://tkpublish.fnn.net.cn/video.mp4", 123L)
                : TkTiktokPullSourceResolver.PullDecision.fallback("HEAD_STATUS"));
        when(scheduled.persist(any(), any(), any(), any(), any())).thenReturn("/managed/video.mp4");
        doAnswer(call -> { ((TkTiktokPublishTaskDO) call.getArgument(0)).setId(2L); return 1; })
                .when(tasks).insert(any(TkTiktokPublishTaskDO.class));
        cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo.TkTiktokPublishCreateReqVO request =
                new cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo.TkTiktokPublishCreateReqVO();
        request.setPostMode("DIRECT_POST");
        request.setScheduledAt("2099-09-26T18:00:00+08:00");
        TkGenerationTaskDO generation = TkGenerationTaskDO.builder().id(10L).companyId(20L).outputUrl(original).build();
        generation.setTenantId(8L);
        ReflectionTestUtils.invokeMethod(f.service, "createPublishTaskWithinTenant", request, generation, null,
                Collections.singletonList(TkTiktokAccountDO.builder().id(1L).authStatus("AUTHORIZED").build()));
        org.mockito.ArgumentCaptor<TkTiktokPublishTaskDO> task = org.mockito.ArgumentCaptor.forClass(TkTiktokPublishTaskDO.class);
        verify(tasks).insert(task.capture());
        assertEquals(expectedPolicy, task.getValue().getTransferPolicy());
        assertEquals(original, task.getValue().getVideoUrl());
        if (candidate && anonymousAccessible) {
            verify(scheduled, never()).persist(any(), any(), any(), any(), any());
            assertNull(task.getValue().getScheduledLocalPath());
        } else {
            verify(scheduled).persist(any(), any(), eq(original), any(), any());
        }
        f.service.destroy();
    }

    @Test
    void losingAtomicRetryCannotResetOrEnqueueANewerAttempt() {
        Fixture f = retryFixture();
        f.detail.setStatus("FAILED");
        f.detail.setRetryCount(7);
        when(f.details.update(any(), any())).thenReturn(0);
        assertThrows(IllegalStateException.class, () -> f.service.retry(3L));
        assertEquals("FAILED", f.detail.getStatus());
        assertEquals(7, f.detail.getRetryCount());
        verify(f.details, times(1)).update(any(), any());
        f.service.destroy();
    }

    @Test
    void knownIdRetryRequiresAuthoritativeProviderFailedAndRejectsUncertainPhase() {
        for (String phase : new String[]{"INIT_CONFIRMED", "INIT_IN_FLIGHT", "RECOVERY_REQUIRED"}) {
            Fixture f = retryFixture();
            f.detail.setStatus("FAILED");
            f.detail.setPublishId("official-id");
            f.detail.setInitPhase(phase);
            f.detail.setTiktokStatus("PROCESSING_DOWNLOAD");
            assertThrows(IllegalStateException.class, () -> f.service.retry(3L));
            verify(f.details, never()).update(any(), any());
            f.service.destroy();
        }
    }

    private Fixture retryFixture() {
        Fixture f = new Fixture();
        ReflectionTestUtils.setField(f.service, "dataScopeService",
                mock(cn.iocoder.yudao.module.tk.service.scope.TkDataScopeService.class));
        when(f.details.selectById(3L)).thenReturn(f.detail);
        return f;
    }

    @Test
    void knownIdLateLocalErrorRemainsEligibleForPollingWithoutReinit() {
        Fixture f = new Fixture();
        when(f.api.initVideoPost(any(), any(), anyMap())).thenReturn(
                new TkTiktokApiClient.PublishResult(true, "official-id", null, null, null));
        TkTiktokAccountMapper accounts = (TkTiktokAccountMapper) ReflectionTestUtils.getField(f.service, "accountMapper");
        doThrow(new IllegalStateException("local account save failed")).when(accounts).updateById(any(TkTiktokAccountDO.class));
        ReflectionTestUtils.invokeMethod(f.service, "processDetail", f.detail);
        assertEquals("PROCESSING", f.detail.getStatus());
        assertEquals("RECOVERY_REQUIRED", f.detail.getInitPhase());
        assertEquals("official-id", f.detail.getPublishId());
        when(f.api.fetchPostStatusOnce(any(), any())).thenReturn(
                new TkTiktokApiClient.PostStatusResult(true, "PROCESSING_DOWNLOAD", null, null, Collections.emptyList()));
        ReflectionTestUtils.invokeMethod(f.service, "syncProcessingDetail", f.detail);
        verify(f.api).fetchPostStatusOnce("token", "official-id");
        verify(f.api, times(1)).initVideoPost(any(), any(), anyMap());
        f.service.destroy();
    }

    @Test
    void staleOldAttemptWritesFenceBothTerminalAndProcessingResults() {
        for (String status : new String[]{"FAILED", "PROCESSING_DOWNLOAD"}) {
            Fixture f = new Fixture();
            f.detail.setStatus("PROCESSING");
            f.detail.setRetryCount(7);
            f.detail.setPublishId("old-id");
            // Simulate a newer retry already stored: the old snapshot must lose its CAS.
            when(f.details.update(any(), any())).thenAnswer(call -> {
                com.baomidou.mybatisplus.core.conditions.Wrapper<?> wrapper = call.getArgument(1);
                String sql = wrapper.getSqlSegment();
                assertTrue(sql.contains("retry_count"));
                assertTrue(sql.contains("publish_id"));
                java.util.Collection<?> values = ((com.baomidou.mybatisplus.core.conditions.AbstractWrapper<?, ?, ?>) wrapper).getParamNameValuePairs().values();
                assertTrue(values.contains(7));
                assertTrue(values.contains("old-id"));
                return 0;
            });
            ReflectionTestUtils.invokeMethod(f.service, "applyPostStatusResult", f.detail, 1L,
                    new TkTiktokApiClient.PostStatusResult(true, status, null, null, Collections.emptyList()));
            verify(f.details, times(1)).update(any(), any());
            f.service.destroy();
        }
    }

    @Test
    void staleUnknownInitializationIsClassifiedBeforeDuePolling() {
        Fixture f = new Fixture();
        when(f.details.selectDueProcessingList(any(), anyInt())).thenReturn(Collections.emptyList());
        f.service.syncStaleProcessingStatus(25);
        verify(f.details).classifyStaleUnknownInit(any(), eq(25));
        verify(f.api, never()).initVideoPost(any(), any(), anyMap());
        f.service.destroy();
    }

    @Test
    void unauthorizedKnownIdIsDeferredWithoutAnyRemoteCalls() {
        Fixture f = new Fixture();
        f.detail.setStatus("PROCESSING");
        f.detail.setPublishId("official-id");
        TkTiktokAccountMapper accounts = (TkTiktokAccountMapper) ReflectionTestUtils.getField(f.service, "accountMapper");
        when(accounts.selectById(1L)).thenReturn(TkTiktokAccountDO.builder().id(1L).authStatus("EXPIRED").build());
        ReflectionTestUtils.invokeMethod(f.service, "syncProcessingDetail", f.detail);
        assertEquals("PROCESSING", f.detail.getStatus());
        assertEquals("RECOVERY_REQUIRED", f.detail.getInitPhase());
        assertNotNull(f.detail.getNextPollTime());
        verify(f.api, never()).fetchPostStatusOnce(any(), any());
        f.service.destroy();
    }

    @Test
    void failedResponsePersistenceRetainsOfficialIdWithOriginalNullIdFence() {
        Fixture f = new Fixture();
        when(f.api.initVideoPost(any(), any(), anyMap())).thenReturn(
                new TkTiktokApiClient.PublishResult(true, "official-id", null, null, null));
        when(f.details.update(any(), any())).thenReturn(1, 1, 0, 1);
        ReflectionTestUtils.invokeMethod(f.service, "processDetail", f.detail);
        assertEquals("PROCESSING", f.detail.getStatus());
        assertEquals("RECOVERY_REQUIRED", f.detail.getInitPhase());
        assertEquals("official-id", f.detail.getPublishId());
        org.mockito.ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.Wrapper<TkTiktokPublishDetailDO>> writes =
                org.mockito.ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);
        verify(f.details, times(4)).update(any(), writes.capture());
        assertTrue(writes.getAllValues().get(3).getSqlSegment().contains("publish_id IS NULL"));
        f.service.destroy();
    }

    @Test
    void dueQueryIncludesKnownIdRecoveryRows() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), TkTiktokPublishDetailDO.class);
        TkTiktokPublishDetailMapper mapper = mock(TkTiktokPublishDetailMapper.class, CALLS_REAL_METHODS);
        when(mapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(Collections.emptyList());
        mapper.selectDueProcessingList(java.time.LocalDateTime.now(), 10);
        org.mockito.ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.Wrapper<TkTiktokPublishDetailDO>> query =
                org.mockito.ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);
        verify(mapper).selectList(query.capture());
        assertTrue(query.getValue().getSqlSegment().contains("publish_id IS NOT NULL"));
        assertFalse(query.getValue().getSqlSegment().contains("tiktok_status"));
    }

    @Test
    void knownFileUploadIdWithoutUploadUrlIsRecoveryInsteadOfRetryableFailure() throws Exception {
        Fixture f = new Fixture();
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress(0), 0);
        server.createContext("/video.mp4", exchange -> {
            byte[] bytes = new byte[300];
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            TkTiktokPublishTaskMapper tasks = (TkTiktokPublishTaskMapper) ReflectionTestUtils.getField(f.service, "publishTaskMapper");
            when(tasks.selectById(2L)).thenReturn(TkTiktokPublishTaskDO.builder().id(2L).transferPolicy("FILE_UPLOAD")
                    .videoUrl("http://localhost:" + server.getAddress().getPort() + "/video.mp4").build());
            when(f.api.initVideoPost(any(), any(), anyMap())).thenReturn(
                    new TkTiktokApiClient.PublishResult(true, "official-id", null, null, null));
            ReflectionTestUtils.invokeMethod(f.service, "processDetail", f.detail);
            assertEquals("PROCESSING", f.detail.getStatus());
            assertEquals("RECOVERY_REQUIRED", f.detail.getInitPhase());
            assertEquals("official-id", f.detail.getPublishId());
            verify(f.api, never()).uploadVideoChunks(any(), any(), any());
        } finally {
            server.stop(0);
            f.service.destroy();
        }
    }

    @Test
    void historicalTwoArgumentResolverStillAcceptsVerifiedCustomUrl() {
        Fixture f = new Fixture();
        Object source = ReflectionTestUtils.invokeMethod(f.service, "resolveUploadSource",
                "https://tkpublish.fnn.net.cn/video.mp4", "video/mp4");
        assertEquals(true, ReflectionTestUtils.getField(source, "pullFromUrl"));
        assertEquals("https://tkpublish.fnn.net.cn/video.mp4", ReflectionTestUtils.getField(source, "pullUrl"));
        f.service.destroy();
    }

    @Test
    void staleUnknownInitSqlOnlyClassifiesAndNeverClearsRemoteBoundary() throws Exception {
        org.apache.ibatis.annotations.Update update = TkTiktokPublishDetailMapper.class
                .getMethod("classifyStaleUnknownInit", java.time.LocalDateTime.class, int.class)
                .getAnnotation(org.apache.ibatis.annotations.Update.class);
        String sql = String.join(" ", update.value());
        assertTrue(sql.contains("init_phase = 'INIT_IN_FLIGHT'"));
        assertTrue(sql.contains("publish_id IS NULL OR publish_id = ''"));
        assertTrue(sql.contains("init_started_at <= #{deadline}"));
        assertTrue(sql.contains("LIMIT #{limit}"));
        assertFalse(sql.contains("SET publish_id"));
        assertFalse(sql.contains("status = 'PENDING'"));
    }

    @Test
    void knownIdLocalFailedWithoutRemoteEvidenceCannotRetry() {
        Fixture f = retryFixture(); f.detail.setStatus("FAILED"); f.detail.setPublishId("old-id");
        f.detail.setTiktokStatus("FAILED"); f.detail.setInitPhase(null);
        assertThrows(IllegalStateException.class, () -> f.service.retry(3L));
        verify(f.details, never()).update(any(), any()); f.service.destroy();
    }

    @Test
    void authoritativeRemoteFailureAllowsKnownIdRetry() {
        Fixture f = retryFixture(); f.detail.setStatus("FAILED"); f.detail.setPublishId("old-id");
        f.detail.setTiktokStatus("FAILED"); f.detail.setInitPhase("REMOTE_FAILED");
        when(f.details.update(any(), any())).thenReturn(1);
        assertDoesNotThrow(() -> f.service.retry(3L));
        verify(f.details).update(any(), any()); f.service.destroy();
    }

    @Test
    void terminalLinkCaptureUsesExpectedAttemptFence() {
        Fixture f = new Fixture(); f.detail.setStatus("SUCCESS"); f.detail.setPublishId("old-id");
        f.detail.setRetryCount(2); f.detail.setTiktokStatus("PUBLISH_COMPLETE");
        TkTiktokApiClient.PostStatusResult r = new TkTiktokApiClient.PostStatusResult(true, "PUBLISH_COMPLETE", null, null, Collections.singletonList("post"));
        when(f.api.queryVideoShareUrl(any(), any())).thenReturn(new TkTiktokApiClient.VideoQueryResult(true, "https://tiktok.test/v", null, null));
        ReflectionTestUtils.invokeMethod(f.service, "applyPostStatusResult", f.detail, 1L, r);
        verify(f.details, atLeastOnce()).update(any(), any()); f.service.destroy();
    }

    private static class Fixture {
        final TkTiktokPublishServiceImpl service = new TkTiktokPublishServiceImpl();
        final TkTiktokApiClient api = mock(TkTiktokApiClient.class);
        final TkTiktokPublishDetailMapper details = mock(TkTiktokPublishDetailMapper.class);
        final TkTiktokPublishDetailDO detail = TkTiktokPublishDetailDO.builder()
                .id(3L).accountId(1L).publishTaskId(2L).status("PENDING").postMode("DIRECT_POST").build();
        Fixture() {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), TkTiktokPublishDetailDO.class);
            TkTiktokAccountMapper accounts = mock(TkTiktokAccountMapper.class);
            TkTiktokPublishTaskMapper tasks = mock(TkTiktokPublishTaskMapper.class);
            TkTiktokTokenService tokens = mock(TkTiktokTokenService.class);
            TkTiktokPullSourceResolver resolver = mock(TkTiktokPullSourceResolver.class);
            TkOpenApiSecretCipher cipher = mock(TkOpenApiSecretCipher.class);
            ReflectionTestUtils.setField(service, "apiClient", api);
            ReflectionTestUtils.setField(service, "publishDetailMapper", details);
            ReflectionTestUtils.setField(service, "accountMapper", accounts);
            ReflectionTestUtils.setField(service, "publishTaskMapper", tasks);
            ReflectionTestUtils.setField(service, "tokenService", tokens);
            ReflectionTestUtils.setField(service, "pullSourceResolver", resolver);
            ReflectionTestUtils.setField(service, "secretCipher", cipher);
            ReflectionTestUtils.setField(service, "businessLogService", mock(TkBusinessLogService.class));
            when(accounts.selectById(1L)).thenReturn(TkTiktokAccountDO.builder().id(1L).authStatus("AUTHORIZED").build());
            when(tasks.selectById(2L)).thenReturn(TkTiktokPublishTaskDO.builder().id(2L)
                    .videoUrl("https://tk-video-create.oss-cn-beijing.aliyuncs.com/video.mp4")
                    .transferPolicy("OSS_PULL_V1").build());
            when(details.update(any(), any())).thenReturn(1);
            when(api.isConfigured()).thenReturn(true);
            when(api.getVerifiedPullDomain()).thenReturn("tkpublish.fnn.net.cn");
            when(tokens.getValidAccessToken(1L)).thenReturn("token");
            when(api.queryCreatorInfo("token")).thenReturn(new TkTiktokApiClient.CreatorInfo(true, null,
                    Collections.singletonList("SELF_ONLY"), false, false, false, 300, null));
            when(resolver.resolve(anyString(), eq(1L), anyString())).thenReturn(
                    TkTiktokPullSourceResolver.PullDecision.eligible("https://tkpublish.fnn.net.cn/video.mp4", 300L));
            when(cipher.encrypt(anyString())).thenReturn("ciphertext");
        }
    }
}

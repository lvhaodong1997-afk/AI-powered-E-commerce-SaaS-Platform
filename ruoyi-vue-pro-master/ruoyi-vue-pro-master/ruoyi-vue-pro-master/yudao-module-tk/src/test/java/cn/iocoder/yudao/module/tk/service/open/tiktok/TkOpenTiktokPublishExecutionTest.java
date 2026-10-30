package cn.iocoder.yudao.module.tk.service.open.tiktok;

import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenTiktokPublishDetailDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenTiktokPublishAttemptDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenTiktokMediaDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenTiktokConnectionDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenTiktokPublishTaskDO;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.*;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiSecretCipher;
import cn.iocoder.yudao.module.tk.service.open.platform.TkOpenPublishPlatformAdapter;
import cn.iocoder.yudao.module.tk.service.open.platform.TkOpenPublishPlatformRegistry;
import cn.iocoder.yudao.module.tk.service.tiktok.TkTiktokPullSourceResolver;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadPoolExecutor;
import java.time.LocalDateTime;
import java.nio.file.Path;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TkOpenTiktokPublishExecutionTest {
    private final TkOpenTiktokPublishDetailMapper details = mock(TkOpenTiktokPublishDetailMapper.class);
    private final TkOpenTiktokPublishAttemptService attempts = mock(TkOpenTiktokPublishAttemptService.class);
    private final CapturingExecutor executor = new CapturingExecutor();
    private final CapturingExecutor uploads = new CapturingExecutor();
    private final CapturingExecutor recoveries = new CapturingExecutor();
    private final TkOpenTiktokPublishService service = TkOpenTiktokConnectionTokenTestSupport.wire(new TkOpenTiktokPublishService(
            mock(TkOpenTiktokPublishTaskMapper.class), details, mock(TkOpenTiktokMediaMapper.class),
            mock(TkOpenTiktokConnectionMapper.class), mock(TkOpenApiIdempotencyMapper.class),
            null, null, mock(TkOpenApiSecretCipher.class), null));

    TkOpenTiktokPublishExecutionTest() {
        ReflectionTestUtils.setField(service, "executor", executor);
        ReflectionTestUtils.setField(service, "uploadExecutor", uploads);
        ReflectionTestUtils.setField(service, "recoveryExecutor", recoveries);
        ReflectionTestUtils.setField(service, "attemptService", attempts);
    }

    @AfterEach void stop() { service.destroy(); }

    @BeforeEach void initializeLambdaMetadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                TkOpenTiktokPublishDetailDO.class);
    }

    @Test void recoveryClaimsLeaseOnlyAfterWorkerStarts() {
        TkOpenTiktokPublishDetailDO detail = detail();
        when(details.selectByClientAndDetailId("c", "d")).thenReturn(detail);

        ReflectionTestUtils.invokeMethod(service, "resumeUpload", detail);

        verify(attempts, never()).claimUpload(any(), any());
        assertEquals(1, recoveries.commands.size());
        recoveries.runNext();
        verify(attempts).claimUpload(eq(detail), any());
    }

    @Test void sameRecoveryIsQueuedOnceAndSlotIsReleasedAfterExecution() {
        TkOpenTiktokPublishDetailDO detail = detail();
        when(details.selectByClientAndDetailId("c", "d")).thenReturn(detail);

        ReflectionTestUtils.invokeMethod(service, "resumeUpload", detail);
        ReflectionTestUtils.invokeMethod(service, "resumeUpload", detail);
        assertEquals(1, recoveries.commands.size());
        recoveries.runNext();
        ReflectionTestUtils.invokeMethod(service, "resumeUpload", detail);
        assertEquals(1, recoveries.commands.size());
    }

    @Test void sameDetailIsAdmittedOnceAndSlotIsReleasedAfterExecution() {
        when(details.selectListByClientAndTaskId("c", "t"))
                .thenReturn(Collections.singletonList(detail().setStatus("PENDING").setPublishId(null)));
        ReflectionTestUtils.invokeMethod(service, "submitTask", "c", "t");
        ReflectionTestUtils.invokeMethod(service, "submitTask", "c", "t");
        assertEquals(1, executor.commands.size());
        executor.runNext();
        ReflectionTestUtils.invokeMethod(service, "submitTask", "c", "t");
        assertEquals(1, executor.commands.size());
    }

    @Test void rejectedTaskCanBeSubmittedByNextDurableScan() {
        when(details.selectListByClientAndTaskId("c", "t"))
                .thenReturn(Collections.singletonList(detail().setStatus("PENDING").setPublishId(null)));
        executor.reject = true;
        ReflectionTestUtils.invokeMethod(service, "submitTask", "c", "t");
        executor.reject = false;
        ReflectionTestUtils.invokeMethod(service, "submitTask", "c", "t");
        assertEquals(1, executor.commands.size());
    }

    @Test void rejectedRecoveryDoesNotClaimLeaseAndCanBeQueuedAgain() {
        recoveries.reject = true;
        ReflectionTestUtils.invokeMethod(service, "resumeUpload", detail());
        recoveries.reject = false;
        ReflectionTestUtils.invokeMethod(service, "resumeUpload", detail());
        verify(attempts, never()).claimUpload(any(), any());
        assertEquals(1, recoveries.commands.size());
    }

    @Test void changedRetryIsNotRecoveredUsingQueuedSnapshot() {
        TkOpenTiktokPublishDetailDO old = detail();
        when(details.selectByClientAndDetailId("c", "d")).thenReturn(detail().setRetryCount(1));
        ReflectionTestUtils.invokeMethod(service, "resumeUpload", old);
        recoveries.runNext();
        verify(attempts, never()).claimUpload(any(), any());
    }

    @Test void statusQueryRespectsPersistedBackoff() {
        TkOpenTiktokPublishDetailDO detail = detail().setConnectionId("connection");
        when(attempts.current(detail)).thenReturn(TkOpenTiktokPublishAttemptDO.builder()
                .nextReconcileTime(LocalDateTime.now().plusMinutes(5)).build());
        ReflectionTestUtils.invokeMethod(service, "syncDetail", detail);
        verifyNoInteractions((TkOpenTiktokConnectionMapper)
                ReflectionTestUtils.getField(service, "connectionMapper"));
    }

    @Test void statusQueryFailurePersistsBackoffWithoutFailingPublish() {
        TkOpenTiktokPublishDetailDO detail = detail().setConnectionId("connection");
        TkOpenTiktokPublishAttemptDO attempt = TkOpenTiktokPublishAttemptDO.builder()
                .id(1L).ownerToken("owner").phase("PLATFORM_PROCESSING").build();
        when(attempts.current(detail)).thenReturn(attempt);
        TkOpenTiktokConnectionMapper connections = (TkOpenTiktokConnectionMapper)
                ReflectionTestUtils.getField(service, "connectionMapper");
        when(connections.selectByClientAndConnectionId("c", "connection")).thenReturn(
                TkOpenTiktokConnectionDO.builder().authStatus("AUTHORIZED").accessTokenCipher("cipher")
                        .accessTokenExpireTime(LocalDateTime.now().plusHours(1)).build());
        TkOpenPublishPlatformRegistry registry = mock(TkOpenPublishPlatformRegistry.class);
        TkOpenPublishPlatformAdapter adapter = mock(TkOpenPublishPlatformAdapter.class);
        when(registry.getRequired("TIKTOK")).thenReturn(adapter);
        ReflectionTestUtils.setField(service, "platformRegistry", registry);
        TkOpenApiSecretCipher cipher = (TkOpenApiSecretCipher) ReflectionTestUtils.getField(service, "secretCipher");
        when(cipher.decrypt("cipher")).thenReturn("token");
        when(adapter.fetchPostStatus("token", "official-id"))
                .thenThrow(new IllegalStateException("network timed out"));

        ReflectionTestUtils.invokeMethod(service, "syncDetail", detail);

        verify(attempts).error(attempt, "network timed out");
        assertEquals("PROCESSING", detail.getStatus());
        verify(adapter, never()).initVideoPost(anyString(), anyString(), anyMap());
    }

    @Test void uploadProgressUsesTheExistingConnectionAndPublishId() {
        TkOpenPublishPlatformAdapter adapter = progressAdapter("PROCESSING_UPLOAD", 32_000_000L, true);

        Long confirmed = ReflectionTestUtils.invokeMethod(service, "confirmedUploadProgress",
                "token", adapter, "official-id", 100_000_000L, 123L);

        assertEquals(Long.valueOf(32_000_000L), confirmed);
        verify(adapter).fetchPostStatus("token", "official-id", 123L);
        verify(adapter, never()).initVideoPost(anyString(), anyString(), anyMap());
        verifyNoInteractions(attempts);
    }

    @Test void confirmedPublishedUploadDoesNotNeedAnotherPut() {
        TkOpenPublishPlatformAdapter adapter = progressAdapter("PUBLISH_COMPLETE", null, true);

        Long confirmed = ReflectionTestUtils.invokeMethod(service, "confirmedUploadProgress",
                "token", adapter, "official-id", 100_000_000L, 123L);

        assertEquals(Long.valueOf(100_000_000L), confirmed);
        verify(adapter, never()).initVideoPost(anyString(), anyString(), anyMap());
    }

    @Test void failedProgressQueryCannotBeTreatedAsZeroProgress() {
        TkOpenPublishPlatformAdapter adapter = progressAdapter("FAILED", null, false);

        assertThrows(IllegalStateException.class, () -> ReflectionTestUtils.invokeMethod(service,
                "confirmedUploadProgress", "token", adapter, "official-id", 100_000_000L, 123L));

        verify(adapter, never()).initVideoPost(anyString(), anyString(), anyMap());
    }

    @Test void providerTerminalFailureDefersToExistingTerminalReconciliation() {
        TkOpenPublishPlatformAdapter adapter = progressAdapter("FAILED", null, true);

        assertThrows(IllegalStateException.class, () -> ReflectionTestUtils.invokeMethod(service,
                "confirmedUploadProgress", "token", adapter, "official-id", 100_000_000L, 123L));

        verifyNoInteractions(attempts);
    }

    private TkOpenPublishPlatformAdapter progressAdapter(String status, Long bytes, boolean success) {
        TkOpenPublishPlatformAdapter adapter = mock(TkOpenPublishPlatformAdapter.class);
        when(adapter.fetchPostStatus("token", "official-id", 123L)).thenReturn(
                new TkOpenPublishPlatformAdapter.PublishStatusResult(success, status, null, null,
                        Collections.emptyList(), bytes));
        return adapter;
    }

    @Test void unresolvedInitializationDefersNextScanWithoutRepublishing() {
        TkOpenTiktokPublishDetailDO detail = detail().setPublishId(null).setTiktokStatus("RECOVERY_REQUIRED");
        TkOpenTiktokPublishAttemptDO attempt = TkOpenTiktokPublishAttemptDO.builder()
                .id(1L).ownerToken("owner").phase("INIT_SENT")
                .heartbeatTime(LocalDateTime.now().minusHours(1)).build();
        when(attempts.current(detail)).thenReturn(attempt);

        assertEquals(false, ReflectionTestUtils.invokeMethod(service, "reconcileRecoveryDetail", detail));

        verify(attempts).error(eq(attempt), contains("Awaiting authoritative TikTok result"));
        assertEquals("PROCESSING", detail.getStatus());
        assertTrue(executor.commands.isEmpty());
    }

    @Test void activeInitializationIsNotMarkedAsAReconcileFailure() {
        TkOpenTiktokPublishDetailDO detail = detail().setPublishId(null).setTiktokStatus("RECOVERY_REQUIRED");
        TkOpenTiktokPublishAttemptDO attempt = TkOpenTiktokPublishAttemptDO.builder()
                .id(1L).ownerToken("owner").phase("INIT_SENT")
                .heartbeatTime(LocalDateTime.now()).build();
        when(attempts.current(detail)).thenReturn(attempt);

        assertEquals(false, ReflectionTestUtils.invokeMethod(service, "reconcileRecoveryDetail", detail));

        verify(attempts).recoverUnsent(eq(detail), any());
        verify(attempts).current(detail);
        verify(attempts, never()).error(any(), anyString());
    }

    @Test void defaultExecutorHasTwoWorkersAndNoWaitingQueue() {
        TkOpenTiktokPublishService defaults = TkOpenTiktokConnectionTokenTestSupport.wire(new TkOpenTiktokPublishService(
                null, null, null, null, null, null, null, null, null));
        try {
            Object workers = ReflectionTestUtils.getField(defaults, "executor");
            assertTrue(workers instanceof ThreadPoolExecutor);
            ThreadPoolExecutor pool = (ThreadPoolExecutor) workers;
            assertEquals(2, pool.getMaximumPoolSize());
            assertEquals(0, pool.getQueue().remainingCapacity());
            assertTrue(pool.getRejectedExecutionHandler() instanceof ThreadPoolExecutor.AbortPolicy);
        } finally {
            defaults.destroy();
        }
    }

    @Test void uploadExecutorIsSeparateAndHasNoWaitingQueue() {
        TkOpenTiktokPublishService defaults = TkOpenTiktokConnectionTokenTestSupport.wire(new TkOpenTiktokPublishService(
                null, null, null, null, null, null, null, null, null));
        try {
            Object uploadWorkers = ReflectionTestUtils.getField(defaults, "uploadExecutor");
            assertTrue(uploadWorkers instanceof ThreadPoolExecutor);
            ThreadPoolExecutor pool = (ThreadPoolExecutor) uploadWorkers;
            assertEquals(2, pool.getMaximumPoolSize());
            assertEquals(0, pool.getQueue().remainingCapacity());
            assertNotSame(ReflectionTestUtils.getField(defaults, "executor"), uploadWorkers);
        } finally {
            defaults.destroy();
        }
    }

    @Test void preparationHandsOffBeforeRemoteInitAndDoesNotOwnTheUploadSlot(@TempDir Path directory) throws Exception {
        TkOpenTiktokPublishDetailDO detail = detail().setStatus("PENDING").setPublishId(null).setConnectionId("conn");
        TkOpenPublishPlatformAdapter adapter = prepareFixture(detail, directory);

        assertEquals(true, ReflectionTestUtils.invokeMethod(service, "submitDetail", detail));
        executor.runNext();

        assertTrue(executor.commands.isEmpty());
        assertEquals(1, uploads.commands.size());
        verify(attempts).claim(detail);
        verify(attempts).ready(any(), eq("FILE_UPLOAD"), eq(3L), anyString());
        verify(attempts, never()).beginRemoteInit(any(), any());
        verify(adapter, never()).initVideoPost(anyString(), anyString(), anyMap());
        assertTrue(Files.exists(directory.resolve("video.mp4")));
    }

    @Test void fullUploadLaneReturnsPreparedWorkWithoutInitializingOrFailing(@TempDir Path directory) throws Exception {
        TkOpenTiktokPublishDetailDO detail = detail().setStatus("PENDING").setPublishId(null).setConnectionId("conn");
        TkOpenPublishPlatformAdapter adapter = prepareFixture(detail, directory);
        uploads.reject = true;
        ReflectionTestUtils.invokeMethod(service, "submitDetail", detail);
        executor.runNext();

        verify(attempts).deferUnsent(eq(detail), any());
        verify(attempts, never()).beginRemoteInit(any(), any());
        verify(attempts, never()).error(any(), anyString());
        verify(adapter, never()).initVideoPost(anyString(), anyString(), anyMap());
        assertTrue(executor.commands.isEmpty());
    }

    @Test void fullPreparationLaneLeavesPendingDetailsUnclaimed() {
        TkOpenTiktokPublishDetailDO detail = detail().setStatus("PENDING").setPublishId(null);
        when(details.selectStalePending(any(), eq(100))).thenReturn(Collections.singletonList(detail));
        executor.reject = true;
        assertEquals(0, service.resumeStalePending(100));
        verifyNoInteractions(attempts);
        assertEquals("PENDING", detail.getStatus());
        executor.reject = false;
        assertEquals(1, service.resumeStalePending(100));
        verifyNoInteractions(attempts);
    }

    private TkOpenPublishPlatformAdapter prepareFixture(TkOpenTiktokPublishDetailDO detail, Path directory) throws Exception {
        Path file = directory.resolve("video.mp4");
        Files.write(file, new byte[]{1, 2, 3});
        TkOpenTiktokPublishTaskMapper tasks = (TkOpenTiktokPublishTaskMapper) ReflectionTestUtils.getField(service, "taskMapper");
        TkOpenTiktokMediaMapper media = (TkOpenTiktokMediaMapper) ReflectionTestUtils.getField(service, "mediaMapper");
        TkOpenTiktokConnectionMapper connections = (TkOpenTiktokConnectionMapper) ReflectionTestUtils.getField(service, "connectionMapper");
        TkOpenApiSecretCipher cipher = (TkOpenApiSecretCipher) ReflectionTestUtils.getField(service, "secretCipher");
        TkOpenPublishPlatformRegistry registry = mock(TkOpenPublishPlatformRegistry.class);
        TkOpenPublishPlatformAdapter adapter = mock(TkOpenPublishPlatformAdapter.class);
        ReflectionTestUtils.setField(service, "platformRegistry", registry);
        when(registry.getRequired("TIKTOK")).thenReturn(adapter);
        when(tasks.selectByClientAndTaskId("c", "t")).thenReturn(TkOpenTiktokPublishTaskDO.builder()
                .taskId("t").mediaId("media").postMode("DIRECT_POST").privacyLevel("SELF_ONLY").build());
        when(media.selectByClientAndMediaId("c", "media")).thenReturn(TkOpenTiktokMediaDO.builder()
                .scheduledLocalPath(file.toString()).contentType("video/mp4").build());
        when(connections.selectByClientAndConnectionId("c", "conn")).thenReturn(TkOpenTiktokConnectionDO.builder()
                .authStatus("AUTHORIZED").accessTokenCipher("cipher").accessTokenExpireTime(LocalDateTime.now().plusHours(1)).build());
        when(cipher.decrypt("cipher")).thenReturn("token");
        TkOpenPublishPlatformAdapter.CreatorCapabilities creator = new TkOpenPublishPlatformAdapter.CreatorCapabilities(
                true, null, Collections.singletonList("SELF_ONLY"), false, false, false, null);
        when(adapter.queryCreatorInfo("token")).thenReturn(creator);
        when(attempts.claim(detail)).thenReturn(TkOpenTiktokPublishAttemptDO.builder().id(1L)
                .ownerToken("owner").phase("MATERIAL_PREPARATION").build());
        return adapter;
    }

    @Test void pullInitializationUsesMappedUrlWithoutUploadLaneOrUploadUrl(@TempDir Path directory) throws Exception {
        TkOpenTiktokPublishDetailDO detail = detail().setStatus("PENDING").setPublishId(null).setConnectionId("conn");
        TkOpenPublishPlatformAdapter adapter = prepareFixture(detail, directory);
        TkOpenTiktokPublishTaskMapper tasks = (TkOpenTiktokPublishTaskMapper) ReflectionTestUtils.getField(service, "taskMapper");
        TkOpenTiktokMediaMapper media = (TkOpenTiktokMediaMapper) ReflectionTestUtils.getField(service, "mediaMapper");
        TkTiktokPullSourceResolver resolver = mock(TkTiktokPullSourceResolver.class);
        ReflectionTestUtils.setField(service, "pullSourceResolver", resolver);
        String original = "https://tk-video-create.oss-cn-beijing.aliyuncs.com/TK/video.mp4";
        String mapped = "https://tkpublish.fnn.net.cn/TK/video.mp4";
        when(tasks.selectByClientAndTaskId("c", "t")).thenReturn(TkOpenTiktokPublishTaskDO.builder()
                .taskId("t").mediaId("media").transferPolicy("OSS_PULL_V1")
                .postMode("DIRECT_POST").privacyLevel("SELF_ONLY").build());
        when(media.selectByClientAndMediaId("c", "media")).thenReturn(TkOpenTiktokMediaDO.builder()
                .fileUrl(original).contentType("video/mp4").build());
        when(resolver.resolve(eq(original), any(), any())).thenReturn(
                TkTiktokPullSourceResolver.PullDecision.eligible(mapped, 123L));
        TkOpenApiSecretCipher cipher = (TkOpenApiSecretCipher) ReflectionTestUtils.getField(service, "secretCipher");
        when(cipher.encrypt(mapped)).thenReturn("encrypted-pull");
        ReflectionTestUtils.setField(service, "responseJournal", mock(TkOpenTiktokPublishResponseJournal.class));
        when(adapter.initVideoPost(eq("token"), eq("DIRECT_POST"), anyMap())).thenReturn(
                new TkOpenPublishPlatformAdapter.PublishInitResult(true, "pull-id", null, null, null));

        ReflectionTestUtils.invokeMethod(service, "submitDetail", detail);
        executor.runNext();

        assertTrue(uploads.commands.isEmpty());
        assertEquals("pull-id", detail.getPublishId());
        assertEquals("PROCESSING_DOWNLOAD", detail.getTiktokStatus());
        org.mockito.ArgumentCaptor<java.util.Map> payload = org.mockito.ArgumentCaptor.forClass(java.util.Map.class);
        verify(adapter).initVideoPost(eq("token"), eq("DIRECT_POST"), payload.capture());
        java.util.Map source = (java.util.Map) payload.getValue().get("source_info");
        assertEquals("PULL_FROM_URL", source.get("source"));
        assertEquals(mapped, source.get("video_url"));
        assertFalse(source.containsKey("chunk_size"));
        verify(attempts).savePullSource(any(), eq("encrypted-pull"));
        verify(attempts).saveResponse(eq(detail), any(), eq("pull-id"), isNull());
        verify(adapter, never()).uploadVideo(anyString(), any(), any(), any(), any());
    }

    @Test void immediateRemoteMediaUsesDurableCopyAndRecoveryReusesIt(@TempDir Path directory) throws Exception {
        TkOpenTiktokMediaService mediaService = mock(TkOpenTiktokMediaService.class);
        ReflectionTestUtils.setField(service, "mediaService", mediaService);
        Path video = directory.resolve("video.mp4");
        Files.write(video, new byte[]{1, 2, 3});
        TkOpenTiktokMediaDO media = TkOpenTiktokMediaDO.builder().mediaId("media")
                .clientId("c").fileName("video.mp4").fileUrl("https://127.0.0.1/video.mp4").build();
        doAnswer(call -> {
            media.setScheduledLocalPath(video.toString()).setScheduledDownloadStatus("READY");
            return null;
        }).when(mediaService).prepareForScheduledPublish(media);

        Object first = ReflectionTestUtils.invokeMethod(service, "resolveSource", media, null);
        Object recovery = ReflectionTestUtils.invokeMethod(service, "resolveSource", media, null);

        assertEquals(video.toAbsolutePath(), ReflectionTestUtils.getField(first, "file"));
        assertEquals(video.toAbsolutePath(), ReflectionTestUtils.getField(recovery, "file"));
        assertEquals(false, ReflectionTestUtils.getField(recovery, "temporary"));
        verify(mediaService, times(1)).prepareForScheduledPublish(media);
    }

    @Test void missingRemoteCacheIsRebuiltBeforeRecovery(@TempDir Path directory) throws Exception {
        TkOpenTiktokMediaService mediaService = mock(TkOpenTiktokMediaService.class);
        ReflectionTestUtils.setField(service, "mediaService", mediaService);
        Path recovered = directory.resolve("recovered.mp4");
        Files.write(recovered, new byte[]{1, 2, 3});
        TkOpenTiktokMediaDO media = TkOpenTiktokMediaDO.builder().mediaId("media").clientId("c")
                .fileName("video.mp4").uploadMode("REMOTE").fileUrl("https://example.com/video.mp4")
                .scheduledDownloadStatus("READY").scheduledLocalPath(directory.resolve("missing.mp4").toString()).build();
        doAnswer(call -> { media.setScheduledLocalPath(recovered.toString()); return null; })
                .when(mediaService).prepareForScheduledPublish(media);

        Object source = ReflectionTestUtils.invokeMethod(service, "resolveSource", media, null);

        assertEquals(recovered.toAbsolutePath(), ReflectionTestUtils.getField(source, "file"));
        verify(mediaService).prepareForScheduledPublish(media);
    }

    private TkOpenTiktokPublishDetailDO detail() {
        return TkOpenTiktokPublishDetailDO.builder().id(1L).clientId("c").taskId("t").detailId("d")
                .status("PROCESSING").retryCount(0).publishId("official-id").build();
    }

    private static class CapturingExecutor extends AbstractExecutorService {
        private final List<Runnable> commands = new ArrayList<>();
        private boolean stopped;
        private boolean reject;
        @Override public void execute(Runnable command) {
            if (reject) throw new RejectedExecutionException("test rejection");
            commands.add(command);
        }
        void runNext() { commands.remove(0).run(); }
        @Override public void shutdown() { stopped = true; }
        @Override public List<Runnable> shutdownNow() { stopped = true; return Collections.emptyList(); }
        @Override public boolean isShutdown() { return stopped; }
        @Override public boolean isTerminated() { return stopped; }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return true; }
    }
}

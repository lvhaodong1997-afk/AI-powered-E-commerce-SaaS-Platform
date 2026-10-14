package cn.iocoder.yudao.module.tk.service.open.tiktok;

import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenTiktokPublishDetailDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenTiktokPublishAttemptDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenTiktokMediaDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenTiktokConnectionDO;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.*;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiSecretCipher;
import cn.iocoder.yudao.module.tk.service.open.platform.TkOpenPublishPlatformAdapter;
import cn.iocoder.yudao.module.tk.service.open.platform.TkOpenPublishPlatformRegistry;
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
    private final TkOpenTiktokPublishService service = new TkOpenTiktokPublishService(
            mock(TkOpenTiktokPublishTaskMapper.class), details, mock(TkOpenTiktokMediaMapper.class),
            mock(TkOpenTiktokConnectionMapper.class), mock(TkOpenApiIdempotencyMapper.class),
            null, null, mock(TkOpenApiSecretCipher.class), null);

    TkOpenTiktokPublishExecutionTest() {
        ReflectionTestUtils.setField(service, "executor", executor);
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
        assertEquals(1, executor.commands.size());
        executor.runNext();
        verify(attempts).claimUpload(eq(detail), any());
    }

    @Test void sameRecoveryIsQueuedOnceAndSlotIsReleasedAfterExecution() {
        TkOpenTiktokPublishDetailDO detail = detail();
        when(details.selectByClientAndDetailId("c", "d")).thenReturn(detail);

        ReflectionTestUtils.invokeMethod(service, "resumeUpload", detail);
        ReflectionTestUtils.invokeMethod(service, "resumeUpload", detail);
        assertEquals(1, executor.commands.size());
        executor.runNext();
        ReflectionTestUtils.invokeMethod(service, "resumeUpload", detail);
        assertEquals(1, executor.commands.size());
    }

    @Test void sameTaskIsQueuedOnceAndSlotIsReleasedAfterExecution() {
        ReflectionTestUtils.invokeMethod(service, "submitTask", "c", "t");
        ReflectionTestUtils.invokeMethod(service, "submitTask", "c", "t");
        assertEquals(1, executor.commands.size());
        executor.runNext();
        ReflectionTestUtils.invokeMethod(service, "submitTask", "c", "t");
        assertEquals(1, executor.commands.size());
    }

    @Test void rejectedTaskCanBeSubmittedByNextDurableScan() {
        executor.reject = true;
        ReflectionTestUtils.invokeMethod(service, "submitTask", "c", "t");
        executor.reject = false;
        ReflectionTestUtils.invokeMethod(service, "submitTask", "c", "t");
        assertEquals(1, executor.commands.size());
    }

    @Test void rejectedRecoveryDoesNotClaimLeaseAndCanBeQueuedAgain() {
        executor.reject = true;
        ReflectionTestUtils.invokeMethod(service, "resumeUpload", detail());
        executor.reject = false;
        ReflectionTestUtils.invokeMethod(service, "resumeUpload", detail());
        verify(attempts, never()).claimUpload(any(), any());
        assertEquals(1, executor.commands.size());
    }

    @Test void changedRetryIsNotRecoveredUsingQueuedSnapshot() {
        TkOpenTiktokPublishDetailDO old = detail();
        when(details.selectByClientAndDetailId("c", "d")).thenReturn(detail().setRetryCount(1));
        ReflectionTestUtils.invokeMethod(service, "resumeUpload", old);
        executor.runNext();
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
        TkOpenApiSecretCipher cipher = (TkOpenApiSecretCipher) ReflectionTestUtils.getField(service, "secretCipher");
        when(cipher.decrypt("cipher")).thenReturn("token");
        TkOpenPublishPlatformRegistry registry = mock(TkOpenPublishPlatformRegistry.class);
        TkOpenPublishPlatformAdapter adapter = mock(TkOpenPublishPlatformAdapter.class);
        when(registry.getRequired("TIKTOK")).thenReturn(adapter);
        ReflectionTestUtils.setField(service, "platformRegistry", registry);
        when(adapter.fetchPostStatus("token", "official-id"))
                .thenThrow(new IllegalStateException("network timed out"));

        ReflectionTestUtils.invokeMethod(service, "syncDetail", detail);

        verify(attempts).error(attempt, "network timed out");
        assertEquals("PROCESSING", detail.getStatus());
        verify(adapter, never()).initVideoPost(anyString(), anyString(), anyMap());
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

    @Test void defaultExecutorHasTwoWorkersAndBoundedQueue() {
        TkOpenTiktokPublishService defaults = new TkOpenTiktokPublishService(
                null, null, null, null, null, null, null, null, null);
        try {
            Object workers = ReflectionTestUtils.getField(defaults, "executor");
            assertTrue(workers instanceof ThreadPoolExecutor);
            ThreadPoolExecutor pool = (ThreadPoolExecutor) workers;
            assertEquals(2, pool.getMaximumPoolSize());
            assertEquals(32, pool.getQueue().remainingCapacity());
            assertTrue(pool.getRejectedExecutionHandler() instanceof ThreadPoolExecutor.AbortPolicy);
        } finally {
            defaults.destroy();
        }
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

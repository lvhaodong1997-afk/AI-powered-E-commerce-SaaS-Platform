package cn.iocoder.yudao.module.tk.service.open.tiktok;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

class TkOpenTiktokPublishStatusJobTest {

    @Test
    void shouldRecoverJournalBeforePollingPlatformStatuses() {
        TkOpenTiktokPublishService publishService = mock(TkOpenTiktokPublishService.class);
        TkOpenTiktokPublishStatusJob job = new TkOpenTiktokPublishStatusJob(publishService);

        try {
            job.sync();
            verify(publishService, timeout(2000)).syncStale(100);
            InOrder order = inOrder(publishService);
            order.verify(publishService).recoverJournal(100);
            order.verify(publishService).syncStale(100);
            verify(publishService, timeout(2000)).reconcileRecoveryRequired(100);
            verify(publishService, timeout(2000)).reconcileTerminalCallbacks(100);
        } finally { job.destroy(); }
    }

    @Test void failedJournalScanDoesNotStopStatusOrCallbacks() {
        TkOpenTiktokPublishService service = mock(TkOpenTiktokPublishService.class);
        org.mockito.Mockito.when(service.recoverJournal(100)).thenThrow(new IllegalStateException("disk unavailable"));
        TkOpenTiktokPublishStatusJob job = new TkOpenTiktokPublishStatusJob(service);
        try {
            job.sync();
            verify(service, timeout(2000)).syncStale(100);
            verify(service, timeout(2000)).reconcileTerminalCallbacks(100);
        } finally { job.destroy(); }
    }

    @Test void blockedStatusScanDoesNotBlockDispatchOrAccumulateScans() throws Exception {
        TkOpenTiktokPublishService service = mock(TkOpenTiktokPublishService.class);
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
        when(service.syncStale(100)).thenAnswer(call -> {
            started.countDown();
            release.await(5, TimeUnit.SECONDS);
            return 0;
        });
        TkOpenTiktokPublishStatusJob job = new TkOpenTiktokPublishStatusJob(service);
        try {
            job.sync();
            assertTrue(started.await(2, TimeUnit.SECONDS));
            for (int i = 0; i < 20; i++) job.sync();
            job.dispatchScheduled();
            verify(service).dispatchDueScheduled(100);
            verify(service).resumeStalePending(100);
            verify(service, times(1)).syncStale(100);
            verify(service, timeout(2000).atLeastOnce()).reconcileTerminalCallbacks(100);
        } finally { release.countDown(); job.destroy(); }
    }
}

package cn.iocoder.yudao.module.tk.service.open.tiktok;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

class TkOpenTiktokPublishStatusJobTest {

    @Test
    void shouldRecoverPersistedWorkBeforePollingPlatformStatuses() {
        TkOpenTiktokPublishService publishService = mock(TkOpenTiktokPublishService.class);
        TkOpenTiktokPublishStatusJob job = new TkOpenTiktokPublishStatusJob(publishService);

        job.sync();

        InOrder order = inOrder(publishService);
        order.verify(publishService).recoverJournal(100);
        order.verify(publishService).resumeStalePending(100);
        order.verify(publishService).syncStale(100);
        order.verify(publishService).reconcileRecoveryRequired(100);
        order.verify(publishService).reconcileTerminalCallbacks(100);
    }

    @Test void failedJournalScanDoesNotStopStatusOrCallbacks() {
        TkOpenTiktokPublishService service = mock(TkOpenTiktokPublishService.class);
        org.mockito.Mockito.when(service.recoverJournal(100)).thenThrow(new IllegalStateException("disk unavailable"));
        new TkOpenTiktokPublishStatusJob(service).sync();
        org.mockito.Mockito.verify(service).syncStale(100);
        org.mockito.Mockito.verify(service).reconcileTerminalCallbacks(100);
    }
}

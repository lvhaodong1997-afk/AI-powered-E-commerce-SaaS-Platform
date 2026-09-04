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
        order.verify(publishService).resumeStalePending(100);
        order.verify(publishService).syncStale(100);
    }
}

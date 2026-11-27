package cn.iocoder.yudao.module.tk.service.open.x;

import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenXPublishTaskDO;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenXConnectionMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenXPublishTaskMapper;
import cn.iocoder.yudao.module.tk.service.channel.TkChannelVideoClient;
import cn.iocoder.yudao.module.tk.service.channel.TkOpenXAccess;
import cn.iocoder.yudao.module.tk.service.channel.TkOpenXPublishService;
import cn.iocoder.yudao.module.tk.service.channel.TkOpenXTokenService;
import cn.iocoder.yudao.module.tk.service.channel.TkXPhotoClient;
import cn.iocoder.yudao.module.tk.service.open.api.TkOpenApiCallbackService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TkOpenXPhotoPublishJobTest {
    @Test
    void anInterruptedClaimIsNotSentAgain() {
        TkOpenXPublishTaskMapper tasks = mock(TkOpenXPublishTaskMapper.class);
        TkXPhotoClient photos = mock(TkXPhotoClient.class);
        TkOpenXConnectionMapper connections = mock(TkOpenXConnectionMapper.class);
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        TkOpenXPublishTaskDO task = new TkOpenXPublishTaskDO().setId(10L).setTaskId("xtask_10")
                .setPhotoIds("[\"media_first\"]").setStatus("PROCESSING").setLeaseToken("old")
                .setLeaseUntil(LocalDateTime.now().minusSeconds(1));
        when(tasks.selectByIdForUpdate(10L)).thenReturn(task);
        TkOpenXPublishJob job = new TkOpenXPublishJob(tasks, connections, mock(TkOpenXMediaService.class),
                mock(TkOpenXTokenService.class), mock(TkChannelVideoClient.class), photos,
                mock(TkOpenXPublishService.class), mock(TkOpenXAccess.class),
                mock(TkOpenApiCallbackService.class), transactions);

        job.processPhoto(10L);

        assertEquals("FAILED", task.getStatus());
        assertEquals("PLATFORM_UNCERTAIN", task.getFailCode());
        assertNull(task.getLeaseToken());
        verify(tasks).updateById(task);
        verifyNoInteractions(photos, connections);
    }
}

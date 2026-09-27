package cn.iocoder.yudao.module.tk.service.cleanup;

import cn.iocoder.yudao.module.tk.dal.dataobject.TkTiktokPublishTaskDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenTiktokMediaDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkTiktokPublishTaskMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenTiktokMediaMapper;
import cn.iocoder.yudao.module.tk.service.open.tiktok.TkOpenTiktokMediaService;
import cn.iocoder.yudao.module.tk.service.tiktok.TkTiktokScheduledMediaService;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class TkScheduledMediaCleanupServiceTest {

    @Test
    void cleansTerminalAppTaskAndMarksItCleaned() {
        TkTiktokPublishTaskMapper appMapper = mock(TkTiktokPublishTaskMapper.class);
        TkOpenTiktokMediaMapper openMediaMapper = mock(TkOpenTiktokMediaMapper.class);
        TkTiktokScheduledMediaService appMediaService = mock(TkTiktokScheduledMediaService.class);
        TkOpenTiktokMediaService openMediaService = mock(TkOpenTiktokMediaService.class);
        TkTiktokPublishTaskDO task = TkTiktokPublishTaskDO.builder().id(1L)
                .status("SUCCESS").scheduledLocalPath("/tk-publish-media/8/video.mp4")
                .scheduledMediaStatus("READY").build();
        when(appMapper.selectScheduledMediaCleanupCandidates(100)).thenReturn(Collections.singletonList(task));
        when(appMapper.claimScheduledMediaCleanup(1L)).thenReturn(1);

        TkScheduledMediaCleanupService service = new TkScheduledMediaCleanupService(
                appMapper, openMediaMapper, appMediaService, openMediaService);

        assertEquals(1, service.cleanupTerminalScheduledMedia(100));
        verify(appMediaService).cleanup(task.getScheduledLocalPath());
        verify(appMapper).updateScheduledMediaCleanup(1L, "CLEANED", null);
    }

    @Test
    void keepsAppTaskForRetryWhenCleanupFails() {
        TkTiktokPublishTaskMapper appMapper = mock(TkTiktokPublishTaskMapper.class);
        TkOpenTiktokMediaMapper openMediaMapper = mock(TkOpenTiktokMediaMapper.class);
        TkTiktokScheduledMediaService appMediaService = mock(TkTiktokScheduledMediaService.class);
        TkOpenTiktokMediaService openMediaService = mock(TkOpenTiktokMediaService.class);
        TkTiktokPublishTaskDO task = TkTiktokPublishTaskDO.builder().id(2L)
                .status("FAILED").scheduledLocalPath("/tk-publish-media/8/video.mp4")
                .scheduledMediaStatus("CLEANUP_FAILED").build();
        when(appMapper.selectScheduledMediaCleanupCandidates(100)).thenReturn(Collections.singletonList(task));
        when(appMapper.claimScheduledMediaCleanup(2L)).thenReturn(1);
        doThrow(new IllegalStateException("disk busy")).when(appMediaService).cleanup(task.getScheduledLocalPath());

        TkScheduledMediaCleanupService service = new TkScheduledMediaCleanupService(
                appMapper, openMediaMapper, appMediaService, openMediaService);

        assertEquals(0, service.cleanupTerminalScheduledMedia(100));
        verify(appMapper).updateScheduledMediaCleanup(eq(2L), eq("CLEANUP_FAILED"), eq("disk busy"));
        verify(appMapper, never()).deleteById(any());
    }

    @Test
    void cleansOpenApiMediaOnlyWhenTerminalTaskReferencesIt() {
        TkTiktokPublishTaskMapper appMapper = mock(TkTiktokPublishTaskMapper.class);
        TkOpenTiktokMediaMapper openMediaMapper = mock(TkOpenTiktokMediaMapper.class);
        TkTiktokScheduledMediaService appMediaService = mock(TkTiktokScheduledMediaService.class);
        TkOpenTiktokMediaService openMediaService = mock(TkOpenTiktokMediaService.class);
        TkOpenTiktokMediaDO media = TkOpenTiktokMediaDO.builder().id(3L).clientId("client_b")
                .mediaId("media_3").scheduledLocalPath("/tk-publish-media/client_b/media_3/video.mp4")
                .scheduledDownloadStatus("READY").build();
        when(appMapper.selectScheduledMediaCleanupCandidates(100)).thenReturn(Collections.emptyList());
        when(openMediaMapper.selectScheduledMediaCleanupCandidates(100)).thenReturn(Collections.singletonList(media));
        when(openMediaMapper.claimScheduledMediaCleanup(3L)).thenReturn(1);

        TkScheduledMediaCleanupService service = new TkScheduledMediaCleanupService(
                appMapper, openMediaMapper, appMediaService, openMediaService);

        assertEquals(1, service.cleanupTerminalScheduledMedia(100));
        verify(openMediaService).cleanupScheduledPublishMedia(media);
        verify(openMediaMapper).markScheduledMediaCleanup(3L, "CLEANED", null);
    }
}

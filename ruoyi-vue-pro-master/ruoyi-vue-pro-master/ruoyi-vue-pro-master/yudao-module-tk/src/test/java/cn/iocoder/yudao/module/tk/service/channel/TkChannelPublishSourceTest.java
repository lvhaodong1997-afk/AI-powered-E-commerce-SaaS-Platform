package cn.iocoder.yudao.module.tk.service.channel;

import cn.iocoder.yudao.module.tk.dal.dataobject.social.TkSocialAccountDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.social.TkSocialMediaDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.social.TkSocialPublishTaskDO;
import cn.iocoder.yudao.module.tk.dal.mysql.social.TkSocialPublishDetailMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.social.TkSocialPublishTaskMapper;
import cn.iocoder.yudao.module.tk.service.scope.TkDataScopeService;
import cn.iocoder.yudao.module.tk.service.social.TkSocialMediaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TkChannelPublishSourceTest {
    private final TkChannelAccountService accounts = mock(TkChannelAccountService.class);
    private final TkSocialMediaService media = mock(TkSocialMediaService.class);
    private final TkSocialPublishTaskMapper tasks = mock(TkSocialPublishTaskMapper.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final TkChannelPublishService service = new TkChannelPublishService(accounts, media, tasks,
            mock(TkSocialPublishDetailMapper.class), mock(TkDataScopeService.class), transactions);
    private final TkChannelPublishService.Create request = new TkChannelPublishService.Create();
    private final TkSocialMediaDO video = new TkSocialMediaDO();

    @BeforeEach void setup() {
        request.setAccountId(1L);
        request.setIdempotencyKey("unique-request-key");
        request.setTitle("Test video");
        request.setPrivacy("private");
        request.setForKids(false);
        request.setSynthetic(false);
        video.setId(2L); video.setTenantId(3L); video.setCompanyId(4L); video.setCreator("5");
        video.setMediaType("VIDEO"); video.setMetadataStatus("VERIFIED"); video.setStatus("READY");
        video.setFileSize(1024L); video.setDurationSeconds(2.0); video.setPublishObjectKey("publish.mp4");
        TkSocialAccountDO account = new TkSocialAccountDO();
        account.setId(1L); account.setTenantId(3L); account.setCompanyId(4L); account.setCreator("5");
        account.setStatus("AUTHORIZED");
        for (String channel : Arrays.asList("x", "youtube")) {
            when(accounts.ready(channel)).thenReturn(true);
            when(accounts.owned(channel, 1L)).thenReturn(account);
        }
        when(media.getOwnedVideoUpload(2L)).thenReturn(video);
        when(media.importGenerated(7L)).thenReturn(video);
        when(transactions.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
        when(tasks.insert(any(TkSocialPublishTaskDO.class))).thenAnswer(invocation -> {
            TkSocialPublishTaskDO task = invocation.getArgument(0);
            assertEquals(2L, task.getMediaId());
            task.setId(8L);
            return 1;
        });
    }

    @Test void bothChannelsAcceptAnOwnedVerifiedUpload() {
        request.setMediaId(2L);
        assertEquals("8", service.create("x", request));
        assertEquals("8", service.create("youtube", request));
        verify(media, times(2)).lockReady(2L, 3L);
        verify(media, never()).importGenerated(any());
        verify(media, never()).cleanupUnreferenced(any(), any());
    }

    @Test void generatedVideoKeepsItsExistingPathAndRequestShape() {
        request.setGenerationTaskId(7L);
        assertFalse(TkChannelHttp.JSON.valueToTree(request).has("mediaId"));
        assertEquals("8", service.create("x", request));
        verify(media).importGenerated(7L);
        verify(media).cleanupUnreferenced(2L, 3L);
    }

    @Test void rejectsMissingAndAmbiguousSourcesBeforeMediaAccess() {
        assertThrows(IllegalArgumentException.class, () -> service.create("x", request));
        request.setGenerationTaskId(7L);
        request.setMediaId(2L);
        assertThrows(IllegalArgumentException.class, () -> service.create("x", request));
        verifyNoInteractions(media, tasks);
    }

    @Test void rejectsOtherOwnersUnverifiedVideoAndXDurationLimits() {
        request.setMediaId(2L);
        video.setCreator("another-user");
        assertThrows(IllegalArgumentException.class, () -> service.create("x", request));
        video.setCreator("5"); video.setMetadataStatus("PENDING");
        assertThrows(IllegalArgumentException.class, () -> service.create("youtube", request));
        video.setMetadataStatus("VERIFIED"); video.setDurationSeconds(141.0);
        assertThrows(IllegalArgumentException.class, () -> service.create("x", request));
        verify(tasks, never()).insert(any(TkSocialPublishTaskDO.class));
    }
}

package cn.iocoder.yudao.module.tk.service.tiktok;

import cn.iocoder.yudao.module.tk.dal.dataobject.TkTiktokAccountDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkTiktokContentVideoDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkTiktokContentVideoMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TkTiktokContentDisplayServiceImplTest {

    @Test
    void givesActionableMessageWhenVideoListScopeIsMissing() {
        TkTiktokApiClient.VideoListResult result = new TkTiktokApiClient.VideoListResult(
                false, java.util.Collections.emptyList(), null, false,
                "scope denied", "scope_not_authorized");

        assertEquals("TikTok 账号缺少 video.list 权限，请重新授权后再同步",
                TkTiktokContentDisplayServiceImpl.contentSyncError(result));
    }

    @Test
    void preservesLongExternalTitleBeforeContentVideoPersistence() {
        TkTiktokContentVideoMapper videoMapper = mock(TkTiktokContentVideoMapper.class);
        TkTiktokContentDisplayServiceImpl service = new TkTiktokContentDisplayServiceImpl();
        ReflectionTestUtils.setField(service, "videoMapper", videoMapper);

        TkTiktokAccountDO account = TkTiktokAccountDO.builder()
                .id(10L)
                .companyId(20L)
                .openId("open-10")
                .build();
        account.setTenantId(30L);
        String title = String.join("", Collections.nCopies(300, "x"));
        TkTiktokApiClient.VideoInfo video = new TkTiktokApiClient.VideoInfo(
                "video-10", null, null, null, null, null, null, null, title,
                null, null, null, null, null, null, null);
        when(videoMapper.selectByAccountIdAndVideoId(10L, "video-10")).thenReturn(null);

        ReflectionTestUtils.invokeMethod(service, "upsertVideo", account, video);

        ArgumentCaptor<TkTiktokContentVideoDO> captor = ArgumentCaptor.forClass(TkTiktokContentVideoDO.class);
        verify(videoMapper).insert(captor.capture());
        assertEquals(title, captor.getValue().getTitle());
    }

}

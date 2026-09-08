package cn.iocoder.yudao.module.tk.service.tiktok;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TkTiktokContentDisplayServiceImplTest {

    @Test
    void givesActionableMessageWhenVideoListScopeIsMissing() {
        TkTiktokApiClient.VideoListResult result = new TkTiktokApiClient.VideoListResult(
                false, java.util.Collections.emptyList(), null, false,
                "scope denied", "scope_not_authorized");

        assertEquals("TikTok 账号缺少 video.list 权限，请重新授权后再同步",
                TkTiktokContentDisplayServiceImpl.contentSyncError(result));
    }

}

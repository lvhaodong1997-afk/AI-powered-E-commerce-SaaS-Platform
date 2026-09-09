package cn.iocoder.yudao.module.tk.service.tiktok;

import cn.iocoder.yudao.module.tk.dal.dataobject.TkTiktokAccountDO;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TkTiktokAuthServiceImplTest {

    @Test
    void extractQrAuthCodeDecodesRedirectCode() {
        String code = TkTiktokAuthServiceImpl.extractQrAuthCode(JsonUtils.parseTree(
                "{\"redirect_uri\":\"https://callback.example/?state=s1&code=abc%2B123%26x\"}"));

        assertEquals("abc+123&x", code);
    }

    @Test
    void applyUserInfoReplacesGeneratedNameButKeepsManualRemark() {
        TkTiktokAccountDO generated = TkTiktokAccountDO.builder()
                .openId("open-abcdef")
                .displayName("TikTok账号 abcdef")
                .username("open-abcdef")
                .build();
        TkTiktokAuthServiceImpl.applyUserInfo(generated, new TkTiktokApiClient.UserInfo(
                true, null, "open-abcdef", "union-1", "Real Shop", "real_shop", "https://cdn.example/a.png"));

        assertEquals("Real Shop", generated.getDisplayName());
        assertEquals("real_shop", generated.getUsername());
        assertEquals("https://cdn.example/a.png", generated.getAvatarUrl());

        TkTiktokAccountDO manual = TkTiktokAccountDO.builder()
                .openId("open-abcdef")
                .displayName("客户A主账号")
                .username("old_name")
                .build();
        TkTiktokAuthServiceImpl.applyUserInfo(manual, new TkTiktokApiClient.UserInfo(
                true, null, "open-abcdef", "union-1", "Real Shop", "real_shop", null));

        assertEquals("客户A主账号", manual.getDisplayName());
        assertEquals("real_shop", manual.getUsername());
    }

    @Test
    void applyUserInfoPersistsAccountStats() {
        TkTiktokAccountDO account = TkTiktokAccountDO.builder()
                .openId("open-stats")
                .build();

        TkTiktokAuthServiceImpl.applyUserInfo(account, new TkTiktokApiClient.UserInfo(
                true, null, "open-stats", "union-stats", "Stats Shop", "stats_shop", null,
                1200L, 80L, 45000L, 23L));

        assertEquals(1200L, account.getFollowerCount());
        assertEquals(80L, account.getFollowingCount());
        assertEquals(45000L, account.getLikesCount());
        assertEquals(23L, account.getVideoCount());
        assertNotNull(account.getStatsUpdatedAt());
    }

}

package cn.iocoder.yudao.module.tk.service.tiktok;

import cn.iocoder.yudao.module.tk.dal.dataobject.TkTiktokAccountDO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TkTiktokAuthServiceImplTest {

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

}

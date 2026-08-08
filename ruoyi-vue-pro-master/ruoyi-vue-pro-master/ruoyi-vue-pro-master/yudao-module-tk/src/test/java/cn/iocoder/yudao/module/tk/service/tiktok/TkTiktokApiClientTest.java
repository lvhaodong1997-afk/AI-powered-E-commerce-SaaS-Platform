package cn.iocoder.yudao.module.tk.service.tiktok;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TkTiktokApiClientTest {

    @Test
    void parseUserInfoExtractsBasicProfile() {
        TkTiktokApiClient.UserInfo userInfo = TkTiktokApiClient.parseUserInfo(JsonUtils.parseTree(
                "{\"data\":{\"user\":{\"open_id\":\"open-1\",\"union_id\":\"union-1\",\"display_name\":\"Shop Main\",\"username\":\"shop_main\",\"avatar_url\":\"https://cdn.example/avatar.png\"}},\"error\":{\"code\":\"ok\"}}"
        ));

        assertTrue(userInfo.isSuccess());
        assertEquals("open-1", userInfo.getOpenId());
        assertEquals("union-1", userInfo.getUnionId());
        assertEquals("Shop Main", userInfo.getDisplayName());
        assertEquals("shop_main", userInfo.getUsername());
        assertEquals("https://cdn.example/avatar.png", userInfo.getAvatarUrl());
    }

    @Test
    void parseUserInfoReturnsFailureOnApiError() {
        TkTiktokApiClient.UserInfo userInfo = TkTiktokApiClient.parseUserInfo(JsonUtils.parseTree(
                "{\"error\":{\"code\":\"access_token_invalid\",\"message\":\"token invalid\",\"log_id\":\"abc\"}}"
        ));

        assertEquals(false, userInfo.isSuccess());
        assertEquals("access_token_invalid：token invalid，log_id=abc", userInfo.getFailReason());
        assertNull(userInfo.getDisplayName());
    }

}

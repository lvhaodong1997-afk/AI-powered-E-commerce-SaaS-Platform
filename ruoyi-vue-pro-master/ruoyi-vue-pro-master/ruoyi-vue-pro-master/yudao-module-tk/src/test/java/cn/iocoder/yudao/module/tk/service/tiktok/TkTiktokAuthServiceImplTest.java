package cn.iocoder.yudao.module.tk.service.tiktok;

import cn.iocoder.yudao.module.tk.dal.dataobject.TkTiktokAccountDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkTiktokAuthSessionDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkTiktokAccountMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.TkTiktokAuthSessionMapper;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    @Test
    void bindsAccountToTheUserWhoCompletesAuthorization() {
        TkTiktokAccountDO account = TkTiktokAccountDO.builder().openId("open-owner").build();

        TkTiktokAuthServiceImpl.applyAuthorizationOwner(account, 226L);

        assertEquals("226", account.getCreator());
    }

    @Test
    void successfulReauthorizationRestoresExistingAccountAndClearsStaleFailure() {
        TkTiktokApiClient apiClient = mock(TkTiktokApiClient.class);
        TkTiktokTokenCipher tokenCipher = mock(TkTiktokTokenCipher.class);
        TkTiktokAccountMapper accountMapper = mock(TkTiktokAccountMapper.class);
        TkTiktokAuthSessionMapper authSessionMapper = mock(TkTiktokAuthSessionMapper.class);
        TkTiktokAuthServiceImpl service = new TkTiktokAuthServiceImpl();
        ReflectionTestUtils.setField(service, "apiClient", apiClient);
        ReflectionTestUtils.setField(service, "tokenCipher", tokenCipher);
        ReflectionTestUtils.setField(service, "accountMapper", accountMapper);
        ReflectionTestUtils.setField(service, "authSessionMapper", authSessionMapper);

        TkTiktokAccountDO account = TkTiktokAccountDO.builder()
                .id(88L).openId("open-reauthed").status(1).authStatus("UNAUTHORIZED")
                .tokenStatus("INVALID").failReason("用户已解绑 TikTok 授权").build();
        account.setTenantId(8L);
        TkTiktokAuthSessionDO session = TkTiktokAuthSessionDO.builder()
                .authType("QR_CODE").build();
        session.setTenantId(8L);
        session.setUserId(226L);
        when(apiClient.exchangeCode("oauth-code", null, null)).thenReturn(JsonUtils.parseTree(
                "{\"data\":{\"access_token\":\"access-new\",\"refresh_token\":\"refresh-new\","
                        + "\"open_id\":\"open-reauthed\",\"expires_in\":86400,\"refresh_expires_in\":31536000}}"));
        when(apiClient.getDefaultScopes()).thenReturn("user.info.basic,video.publish");
        when(apiClient.queryUserInfo("access-new")).thenReturn(new TkTiktokApiClient.UserInfo(
                true, null, "open-reauthed", "union-1", "Reauthorized", "reauthorized", null));
        when(accountMapper.selectByTenantIdAndOpenId(8L, "open-reauthed")).thenReturn(account);
        when(tokenCipher.encrypt(anyString())).thenAnswer(invocation -> "enc-" + invocation.getArgument(0));

        ReflectionTestUtils.invokeMethod(service, "exchangeCodeAndSaveAccount", session, "oauth-code");

        assertEquals(0, account.getStatus());
        assertEquals("AUTHORIZED", account.getAuthStatus());
        assertEquals("VALID", account.getTokenStatus());
        org.junit.jupiter.api.Assertions.assertNull(account.getFailReason());
    }

}

package cn.iocoder.yudao.module.tk.service.open.tiktok;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.tk.controller.open.tiktok.vo.TkOpenTiktokAuthVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenTiktokConnectionDO;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenTiktokAuthSessionMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenTiktokConnectionMapper;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiContext;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiPrincipal;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiSecretCipher;
import cn.iocoder.yudao.module.tk.service.open.api.TkOpenApiCallbackService;
import cn.iocoder.yudao.module.tk.service.open.platform.TkOpenPublishPlatformAdapter;
import cn.iocoder.yudao.module.tk.service.open.platform.TkOpenPublishPlatformRegistry;
import cn.iocoder.yudao.module.tk.service.tiktok.TkTiktokApiClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

class TkOpenTiktokAccountProfileTest {

    @AfterEach
    void clearContext() {
        TkOpenApiContext.clear();
    }

    @Test
    void userInfoParserIncludesProfileFieldsAndStats() throws Exception {
        Method parser = TkTiktokApiClient.class.getDeclaredMethod("parseUserInfo",
                com.fasterxml.jackson.databind.JsonNode.class);
        parser.setAccessible(true);
        TkTiktokApiClient.UserInfo user = (TkTiktokApiClient.UserInfo) parser.invoke(null, JsonUtils.parseTree(
                "{\"data\":{\"user\":{"
                        + "\"open_id\":\"open-1\",\"display_name\":\"Creator\","
                        + "\"username\":\"creator\",\"avatar_url\":\"https://cdn/avatar.png\","
                        + "\"bio_description\":\"Creator bio\","
                        + "\"profile_deep_link\":\"https://www.tiktok.com/@creator\","
                        + "\"is_verified\":true,\"follower_count\":1200,"
                        + "\"following_count\":80,\"likes_count\":45000,\"video_count\":23}},"
                        + "\"error\":{\"code\":\"ok\"}}"));

        assertEquals("Creator bio", property(user, "bioDescription"));
        assertEquals("https://www.tiktok.com/@creator", property(user, "profileDeepLink"));
        assertEquals(true, property(user, "verified"));
        assertEquals(1200L, property(user, "followerCount"));
        assertEquals(80L, property(user, "followingCount"));
        assertEquals(45000L, property(user, "likesCount"));
        assertEquals(23L, property(user, "videoCount"));
    }

    @Test
    void connectionAndResponsesExposeProfileFields() throws Exception {
        for (String field : new String[]{"bioDescription", "profileDeepLink", "verified",
                "followerCount", "followingCount", "likesCount", "videoCount", "statsUpdatedAt"}) {
            assertNotNull(TkOpenTiktokConnectionDO.class.getDeclaredField(field));
            assertNotNull(TkOpenTiktokAuthVO.ConnectionResp.class.getDeclaredField(field));
            assertNotNull(TkOpenTiktokAuthVO.SessionStatusResp.class.getDeclaredField(field));
        }
    }

    @Test
    void refreshProfileQueriesTikTokAndPersistsLatestFields() throws Exception {
        TkOpenTiktokConnectionMapper connectionMapper = mock(TkOpenTiktokConnectionMapper.class);
        TkOpenPublishPlatformRegistry registry = mock(TkOpenPublishPlatformRegistry.class);
        TkOpenPublishPlatformAdapter adapter = mock(TkOpenPublishPlatformAdapter.class);
        TkOpenApiSecretCipher cipher = mock(TkOpenApiSecretCipher.class);
        TkOpenTiktokConnectionDO connection = TkOpenTiktokConnectionDO.builder()
                .id(1L).connectionId("conn-1").clientId("client-a").authStatus("AUTHORIZED")
                .accessTokenCipher("access-cipher")
                .accessTokenExpireTime(LocalDateTime.now().plusHours(1)).build();
        TkOpenPublishPlatformAdapter.PlatformUser user = new TkOpenPublishPlatformAdapter.PlatformUser(
                true, "open-1", "Creator", "creator", "https://cdn/avatar.png", null);
        setProperty(user, "bioDescription", "Creator bio");
        setProperty(user, "profileDeepLink", "https://www.tiktok.com/@creator");
        setProperty(user, "verified", true);
        setProperty(user, "followerCount", 1200L);
        setProperty(user, "followingCount", 80L);
        setProperty(user, "likesCount", 45000L);
        setProperty(user, "videoCount", 23L);
        when(connectionMapper.selectByClientAndConnectionId("client-a", "conn-1")).thenReturn(connection);
        when(cipher.decrypt("access-cipher")).thenReturn("access-token");
        when(registry.getRequired("TIKTOK")).thenReturn(adapter);
        when(adapter.queryUserInfo("access-token")).thenReturn(user);
        TkOpenTiktokAuthService service = new TkOpenTiktokAuthService(
                mock(TkOpenTiktokAuthSessionMapper.class), connectionMapper, registry, cipher,
                mock(TkOpenApiCallbackService.class), "https://callback", "https://launch");
        TkOpenApiContext.set(new TkOpenApiPrincipal("client-a", "A", "profile"), "req-profile");

        Object response = TkOpenTiktokAuthService.class.getMethod("refreshProfile", String.class)
                .invoke(service, "conn-1");

        assertEquals("Creator bio", property(response, "bioDescription"));
        assertEquals(1200L, property(response, "followerCount"));
        assertEquals(true, property(response, "verified"));
        verify(connectionMapper).updateById(connection);
    }

    private static Object property(Object bean, String name) throws Exception {
        Field field = bean.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(bean);
    }

    private static void setProperty(Object bean, String name, Object value) throws Exception {
        Field field = bean.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(bean, value);
    }
}

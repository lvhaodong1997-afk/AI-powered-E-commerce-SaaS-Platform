package cn.iocoder.yudao.module.tk.service.open.tiktok;

import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenTiktokConnectionDO;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenTiktokConnectionMapper;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiSecretCipher;
import cn.iocoder.yudao.module.tk.service.open.platform.TkOpenPublishPlatformAdapter;
import cn.iocoder.yudao.module.tk.service.open.platform.TkOpenPublishPlatformRegistry;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TkOpenTiktokConnectionTokenServiceTest {

    @Test
    void refreshesLongInactiveAuthorizedConnectionAndPersistsRotatedRefreshToken() throws Exception {
        Fixture fixture = new Fixture();
        TkOpenTiktokConnectionDO connection = connection(101L, LocalDateTime.now().minusMinutes(1));
        connection.setLastPublishTime(null);
        when(fixture.mapper.selectExpiringAuthorizedConnections(any(), any(), eq(0L), eq(50)))
                .thenReturn(Collections.singletonList(connection));
        when(fixture.mapper.selectById(101L)).thenReturn(connection);
        fixture.stubLock();
        when(fixture.cipher.decrypt("refresh-cipher")).thenReturn("refresh-current");
        when(fixture.cipher.encrypt("access-new")).thenReturn("access-new-cipher");
        when(fixture.cipher.encrypt("refresh-rotated")).thenReturn("refresh-rotated-cipher");
        when(fixture.registry.getRequired("TIKTOK")).thenReturn(fixture.adapter);
        when(fixture.adapter.refreshAccessToken("refresh-current")).thenReturn(
                new TkOpenPublishPlatformAdapter.OAuthTokenResult(true, "access-new", "refresh-rotated",
                        86400L, 31536000L, "user.info.basic", "open-101", null, null));

        int refreshed = fixture.service.refreshExpiringAuthorizedConnections(50);

        assertEquals(1, refreshed);
        assertEquals("access-new-cipher", connection.getAccessTokenCipher());
        assertEquals("refresh-rotated-cipher", connection.getRefreshTokenCipher());
        assertEquals("AUTHORIZED", connection.getAuthStatus());
        verify(fixture.adapter).refreshAccessToken("refresh-current");
    }

    @Test
    void doesNotRefreshAccessTokenOutsideRefreshWindow() throws Exception {
        Fixture fixture = new Fixture();
        TkOpenTiktokConnectionDO connection = connection(102L, LocalDateTime.now().plusHours(2));
        when(fixture.cipher.decrypt("access-cipher")).thenReturn("access-current");

        String accessToken = fixture.service.getValidAccessToken(connection, fixture.adapter, false);

        assertEquals("access-current", accessToken);
        verify(fixture.redissonClient, never()).getLock(anyString());
        verify(fixture.adapter, never()).refreshAccessToken(anyString());
    }

    @Test
    void expiredRefreshTokenRequiresReauthorizationWithoutCallingTikTok() throws Exception {
        Fixture fixture = new Fixture();
        TkOpenTiktokConnectionDO connection = connection(103L, LocalDateTime.now().minusMinutes(1));
        connection.setRefreshTokenExpireTime(LocalDateTime.now().minusSeconds(1));
        fixture.stubLock();
        when(fixture.mapper.selectById(103L)).thenReturn(connection);

        assertThrows(RuntimeException.class,
                () -> fixture.service.getValidAccessToken(connection, fixture.adapter, false));

        assertEquals("REAUTH_REQUIRED", connection.getAuthStatus());
        assertEquals("INVALID", connection.getTokenStatus());
        verify(fixture.adapter, never()).refreshAccessToken(anyString());
    }

    @Test
    void oneConnectionFailureDoesNotBlockOtherConnections() {
        Fixture fixture = new Fixture();
        TkOpenTiktokConnectionDO first = connection(104L, LocalDateTime.now().minusMinutes(1));
        TkOpenTiktokConnectionDO second = connection(105L, LocalDateTime.now().minusMinutes(1));
        when(fixture.mapper.selectExpiringAuthorizedConnections(any(), any(), eq(0L), eq(50)))
                .thenReturn(Arrays.asList(first, second));
        when(fixture.mapper.selectById(104L)).thenReturn(first);
        when(fixture.mapper.selectById(105L)).thenReturn(second);
        when(fixture.registry.getRequired("TIKTOK")).thenReturn(fixture.adapter);
        when(fixture.redissonClient.getLock(anyString())).thenThrow(new IllegalStateException("temporary"));

        int refreshed = fixture.service.refreshExpiringAuthorizedConnections(50);

        assertEquals(0, refreshed);
        verify(fixture.redissonClient).getLock("tk:tiktok:open-connection:token-refresh:104");
        verify(fixture.redissonClient).getLock("tk:tiktok:open-connection:token-refresh:105");
    }

    @Test
    void forcedRefreshReusesTokenAlreadyRotatedByAnotherWorker() throws Exception {
        Fixture fixture = new Fixture();
        TkOpenTiktokConnectionDO stale = connection(106L, LocalDateTime.now().minusMinutes(1));
        TkOpenTiktokConnectionDO current = connection(106L, LocalDateTime.now().plusHours(20));
        current.setAccessTokenCipher("rotated-access");
        fixture.stubLock();
        when(fixture.mapper.selectById(106L)).thenReturn(current);
        when(fixture.cipher.decrypt("rotated-access")).thenReturn("current-token");

        assertEquals("current-token", fixture.service.getValidAccessToken(stale, fixture.adapter, true));
        assertEquals("rotated-access", stale.getAccessTokenCipher());
        verify(fixture.adapter, never()).refreshAccessToken(any());
    }

    @Test
    void missingPersistentIdCannotBypassRefreshLock() {
        Fixture fixture = new Fixture();
        TkOpenTiktokConnectionDO connection = connection(null, LocalDateTime.now().minusMinutes(1));
        assertThrows(RuntimeException.class,
                () -> fixture.service.getValidAccessToken(connection, fixture.adapter, true));
        verify(fixture.adapter, never()).refreshAccessToken(any());
    }

    private static TkOpenTiktokConnectionDO connection(Long id, LocalDateTime accessExpireTime) {
        return TkOpenTiktokConnectionDO.builder()
                .id(id)
                .accessTokenCipher("access-cipher")
                .refreshTokenCipher("refresh-cipher")
                .accessTokenExpireTime(accessExpireTime)
                .refreshTokenExpireTime(LocalDateTime.now().plusDays(30))
                .tokenStatus("NORMAL")
                .authStatus("AUTHORIZED")
                .build();
    }

    private static class Fixture {
        private final TkOpenTiktokConnectionMapper mapper = mock(TkOpenTiktokConnectionMapper.class);
        private final TkOpenApiSecretCipher cipher = mock(TkOpenApiSecretCipher.class);
        private final RedissonClient redissonClient = mock(RedissonClient.class);
        private final TkOpenPublishPlatformRegistry registry = mock(TkOpenPublishPlatformRegistry.class);
        private final TkOpenPublishPlatformAdapter adapter = mock(TkOpenPublishPlatformAdapter.class);
        private final RLock lock = mock(RLock.class);
        private final TkOpenTiktokConnectionTokenService service = new TkOpenTiktokConnectionTokenService();

        private Fixture() {
            com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                    new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""),
                    TkOpenTiktokConnectionDO.class);
            when(mapper.update(any(), any())).thenReturn(1);
            ReflectionTestUtils.setField(service, "connectionMapper", mapper);
            ReflectionTestUtils.setField(service, "secretCipher", cipher);
            ReflectionTestUtils.setField(service, "redissonClient", redissonClient);
            ReflectionTestUtils.setField(service, "platformRegistry", registry);
        }

        private void stubLock() throws InterruptedException {
            when(redissonClient.getLock(anyString())).thenReturn(lock);
            when(lock.isHeldByCurrentThread()).thenReturn(true);
            when(lock.tryLock(10L, TimeUnit.SECONDS)).thenReturn(true);
        }
    }
}

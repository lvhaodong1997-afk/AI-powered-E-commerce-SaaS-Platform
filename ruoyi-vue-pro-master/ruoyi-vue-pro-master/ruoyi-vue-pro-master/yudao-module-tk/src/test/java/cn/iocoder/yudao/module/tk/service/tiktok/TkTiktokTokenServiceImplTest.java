package cn.iocoder.yudao.module.tk.service.tiktok;

import cn.iocoder.yudao.module.tk.dal.dataobject.TkTiktokAccountDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkTiktokAccountMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TkTiktokTokenServiceImplTest {

    @Test
    void getValidAccessTokenReusesTokenOutsideRefreshWindow() {
        Fixture fixture = new Fixture();
        TkTiktokAccountDO account = account(10L, LocalDateTime.now().plusHours(2),
                LocalDateTime.now().plusDays(30));
        when(fixture.accountMapper.selectById(10L)).thenReturn(account);
        when(fixture.tokenCipher.decrypt("access-cipher")).thenReturn("access-current");

        String accessToken = fixture.service.getValidAccessToken(10L);

        assertEquals("access-current", accessToken);
        verify(fixture.apiClient, never()).refreshAccessToken(any());
        verify(fixture.redissonClient, never()).getLock(anyString());
    }

    @Test
    void getValidAccessTokenRefreshesAndPersistsRotatedTokens() throws Exception {
        Fixture fixture = new Fixture();
        TkTiktokAccountDO account = account(11L, LocalDateTime.now().plusMinutes(3),
                LocalDateTime.now().plusDays(30));
        fixture.stubLock();
        when(fixture.accountMapper.selectById(11L)).thenReturn(account);
        when(fixture.tokenCipher.decrypt("refresh-cipher")).thenReturn("refresh-current");
        when(fixture.tokenCipher.encrypt("access-new")).thenReturn("access-new-cipher");
        when(fixture.tokenCipher.encrypt("refresh-rotated")).thenReturn("refresh-new-cipher");
        when(fixture.apiClient.refreshAccessToken("refresh-current")).thenReturn(new TkTiktokApiClient.TokenRefreshResult(
                true, "access-new", "refresh-rotated", 86400L, 31536000L,
                "user.info.basic,video.publish", "open-11", null, null));

        String accessToken = fixture.service.getValidAccessToken(11L);

        assertEquals("access-new", accessToken);
        ArgumentCaptor<TkTiktokAccountDO> captor = ArgumentCaptor.forClass(TkTiktokAccountDO.class);
        verify(fixture.accountMapper).updateById(captor.capture());
        TkTiktokAccountDO updated = captor.getValue();
        assertEquals("access-new-cipher", updated.getAccessTokenCipher());
        assertEquals("refresh-new-cipher", updated.getRefreshTokenCipher());
        assertEquals("VALID", updated.getTokenStatus());
        assertEquals("AUTHORIZED", updated.getAuthStatus());
        assertEquals("user.info.basic,video.publish", updated.getScopes());
        assertTrue(updated.getAccessTokenExpireTime().isAfter(LocalDateTime.now().plusHours(23)));
        assertTrue(updated.getRefreshTokenExpireTime().isAfter(LocalDateTime.now().plusDays(364)));
    }

    @Test
    void refreshRechecksAccountAfterAcquiringLock() throws Exception {
        Fixture fixture = new Fixture();
        TkTiktokAccountDO expiring = account(12L, LocalDateTime.now().plusMinutes(1),
                LocalDateTime.now().plusDays(30));
        TkTiktokAccountDO refreshed = account(12L, LocalDateTime.now().plusHours(20),
                LocalDateTime.now().plusDays(30));
        refreshed.setAccessTokenCipher("access-refreshed-cipher");
        fixture.stubLock();
        when(fixture.accountMapper.selectById(12L)).thenReturn(expiring, refreshed);
        when(fixture.tokenCipher.decrypt("access-refreshed-cipher")).thenReturn("access-from-other-instance");

        String accessToken = fixture.service.getValidAccessToken(12L);

        assertEquals("access-from-other-instance", accessToken);
        verify(fixture.apiClient, never()).refreshAccessToken(any());
        verify(fixture.accountMapper, never()).updateById(any(TkTiktokAccountDO.class));
    }

    @Test
    void missingOrExpiredRefreshTokenRequiresReauthorization() throws Exception {
        Fixture missingFixture = new Fixture();
        TkTiktokAccountDO missing = account(13L, LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusDays(30));
        missing.setRefreshTokenCipher(null);
        missingFixture.stubLock();
        when(missingFixture.accountMapper.selectById(13L)).thenReturn(missing);

        IllegalStateException missingError = assertThrows(IllegalStateException.class,
                () -> missingFixture.service.getValidAccessToken(13L));

        assertTrue(missingError.getMessage().contains("重新授权"));
        assertEquals("INVALID", missing.getTokenStatus());
        assertEquals("UNAUTHORIZED", missing.getAuthStatus());

        Fixture expiredFixture = new Fixture();
        TkTiktokAccountDO expired = account(14L, LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().minusSeconds(1));
        expiredFixture.stubLock();
        when(expiredFixture.accountMapper.selectById(14L)).thenReturn(expired);

        assertThrows(IllegalStateException.class, () -> expiredFixture.service.getValidAccessToken(14L));
        assertEquals("INVALID", expired.getTokenStatus());
        assertEquals("UNAUTHORIZED", expired.getAuthStatus());
    }

    @Test
    void rejectedRefreshRequiresReauthorizationButTransientFailureDoesNot() throws Exception {
        Fixture rejectedFixture = new Fixture();
        TkTiktokAccountDO rejected = account(15L, LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusDays(30));
        rejectedFixture.stubLock();
        when(rejectedFixture.accountMapper.selectById(15L)).thenReturn(rejected);
        when(rejectedFixture.tokenCipher.decrypt("refresh-cipher")).thenReturn("refresh-current");
        when(rejectedFixture.apiClient.refreshAccessToken("refresh-current")).thenReturn(
                new TkTiktokApiClient.TokenRefreshResult(false, null, null, null, null,
                        null, null, "invalid_grant", "invalid_grant：Refresh Token 已失效，log_id=reject-log"));

        assertThrows(IllegalStateException.class, () -> rejectedFixture.service.getValidAccessToken(15L));
        assertEquals("INVALID", rejected.getTokenStatus());
        assertEquals("UNAUTHORIZED", rejected.getAuthStatus());
        assertTrue(rejected.getFailReason().contains("重新授权"));

        Fixture transientFixture = new Fixture();
        TkTiktokAccountDO transientAccount = account(16L, LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusDays(30));
        transientFixture.stubLock();
        when(transientFixture.accountMapper.selectById(16L)).thenReturn(transientAccount);
        when(transientFixture.tokenCipher.decrypt("refresh-cipher")).thenReturn("refresh-current");
        when(transientFixture.apiClient.refreshAccessToken("refresh-current")).thenReturn(
                new TkTiktokApiClient.TokenRefreshResult(false, null, null, null, null,
                        null, null, "server_error", "server_error：TikTok temporary error，log_id=temp-log"));

        assertThrows(IllegalStateException.class, () -> transientFixture.service.getValidAccessToken(16L));
        assertEquals("REFRESH_FAILED", transientAccount.getTokenStatus());
        assertEquals("AUTHORIZED", transientAccount.getAuthStatus());
        assertTrue(transientAccount.getFailReason().contains("temp-log"));
    }

    @Test
    void refreshExpiringActiveAccountsContinuesAfterIndividualFailure() {
        Fixture fixture = new Fixture();
        TkTiktokAccountDO first = account(17L, LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusDays(30));
        TkTiktokAccountDO second = account(18L, LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusDays(30));
        when(fixture.accountMapper.selectExpiringActiveAccounts(any(), any(), eq(50)))
                .thenReturn(java.util.Arrays.asList(first, second));
        TkTiktokTokenServiceImpl spy = org.mockito.Mockito.spy(fixture.service);
        org.mockito.Mockito.doThrow(new IllegalStateException("temporary"))
                .when(spy).forceRefreshAccessToken(17L);
        org.mockito.Mockito.doReturn("access-ok").when(spy).forceRefreshAccessToken(18L);

        int refreshed = spy.refreshExpiringActiveAccounts(50);

        assertEquals(1, refreshed);
        verify(spy).forceRefreshAccessToken(17L);
        verify(spy).forceRefreshAccessToken(18L);
    }

    private static TkTiktokAccountDO account(Long id, LocalDateTime accessExpireTime,
                                             LocalDateTime refreshExpireTime) {
        TkTiktokAccountDO account = TkTiktokAccountDO.builder()
                .id(id)
                .openId("open-" + id)
                .accessTokenCipher("access-cipher")
                .refreshTokenCipher("refresh-cipher")
                .accessTokenExpireTime(accessExpireTime)
                .refreshTokenExpireTime(refreshExpireTime)
                .tokenStatus("VALID")
                .authStatus("AUTHORIZED")
                .status(0)
                .build();
        account.setTenantId(8L);
        return account;
    }

    private static class Fixture {
        private final TkTiktokAccountMapper accountMapper = mock(TkTiktokAccountMapper.class);
        private final TkTiktokTokenCipher tokenCipher = mock(TkTiktokTokenCipher.class);
        private final TkTiktokApiClient apiClient = mock(TkTiktokApiClient.class);
        private final RedissonClient redissonClient = mock(RedissonClient.class);
        private final RLock lock = mock(RLock.class);
        private final TkTiktokTokenServiceImpl service = new TkTiktokTokenServiceImpl();

        private Fixture() {
            ReflectionTestUtils.setField(service, "accountMapper", accountMapper);
            ReflectionTestUtils.setField(service, "tokenCipher", tokenCipher);
            ReflectionTestUtils.setField(service, "apiClient", apiClient);
            ReflectionTestUtils.setField(service, "redissonClient", redissonClient);
        }

        private void stubLock() throws InterruptedException {
            when(redissonClient.getLock(anyString())).thenReturn(lock);
            when(lock.tryLock(10L, 60L, TimeUnit.SECONDS)).thenReturn(true);
        }
    }

}

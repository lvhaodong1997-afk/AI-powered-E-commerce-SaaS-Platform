package cn.iocoder.yudao.module.tk.service.tiktokshop;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.tk.dal.dataobject.tiktokshop.TkTiktokShopAccountDO;
import cn.iocoder.yudao.module.tk.dal.mysql.tiktokshop.TkTiktokShopAccountMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.redisson.api.*;
import java.time.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class TkTiktokShopTokenServiceTest {
    TkTiktokShopAccountMapper accounts=mock(TkTiktokShopAccountMapper.class);TkTiktokShopApiClient api=mock(TkTiktokShopApiClient.class);
    RedissonClient redis=mock(RedissonClient.class);RLock lock=mock(RLock.class);
    Clock clock=Clock.fixed(Instant.ofEpochSecond(1800000000),ZoneOffset.UTC);
    TkTiktokShopTokenCipher cipher=new TkTiktokShopTokenCipher(TkTiktokShopTokenCipherTest.properties());
    TkTiktokShopTokenService service=new TkTiktokShopTokenService(TkTiktokShopTokenCipherTest.properties(),accounts,api,cipher,redis,clock);
    @BeforeEach void init() throws Exception{TenantContextHolder.setTenantId(1L);when(redis.getLock(anyString())).thenReturn(lock);when(lock.tryLock(0,TimeUnit.SECONDS)).thenReturn(true);when(lock.isHeldByCurrentThread()).thenReturn(true);}
    @AfterEach void clear(){TenantContextHolder.clear();}
    @Test void productPermissionDenialDoesNotRevokeCreatorCredentials() {
        when(accounts.selectById(3L)).thenReturn(account());
        service.reportRejectedToken(1L,1L,3L,4L,new TkTiktokShopApiException("105005","r"));
        verify(accounts,never()).markReauthorization(anyLong(),anyLong(),anyLong());
    }
    @Test void refreshPermissionDenialDoesNotRevokeCreatorCredentials() {
        when(accounts.selectById(3L)).thenReturn(account());
        when(api.refreshToken("refresh")).thenThrow(new TkTiktokShopApiException("105005","r"));
        assertEquals(TkTiktokShopApiException.CREATOR_REFRESH_PENDING,assertThrows(TkTiktokShopApiException.class,()->service.getAccessToken(1L,1L,3L)).getCode());
        verify(accounts).deferRefresh(3L,1L,4L,LocalDateTime.now(clock).plusMinutes(2));
        verify(accounts,never()).markReauthorization(anyLong(),anyLong(),anyLong());
    }
    @Test void workerRequiresKnownTenantCompanyAndNoIgnore() {
        when(accounts.selectById(3L)).thenReturn(account());
        assertThrows(IllegalStateException.class,()->service.getAccessToken(2L,1L,3L));
        assertThrows(IllegalStateException.class,()->service.getAccessToken(1L,2L,3L));
        TenantContextHolder.setIgnore(true);assertThrows(IllegalStateException.class,()->service.getAccessToken(1L,1L,3L));
        verifyNoInteractions(api);
    }
    @Test void rotatingRefreshUsesDistributedLockAndVersionCas() throws Exception {
        when(accounts.selectById(3L)).thenReturn(account());when(api.refreshToken("refresh")).thenReturn(bundle("open"));when(accounts.replaceTokens(any(),eq(4L))).thenReturn(1);
        TkTiktokShopTokenService.AccessToken result=service.getAccessToken(1L,1L,3L);
        assertEquals("new-access",result.getValue());assertEquals(5L,result.getVersion());
        verify(lock).tryLock(0,TimeUnit.SECONDS);verify(accounts).replaceTokens(any(),eq(4L));verify(lock).unlock();
        assertFalse(result.toString().contains("new-access"));
    }
    @Test void disconnectOrReauthDuringRefreshCannotBeOverwritten() throws Exception {
        when(accounts.selectById(3L)).thenReturn(account());when(api.refreshToken("refresh")).thenReturn(bundle("open"));when(accounts.replaceTokens(any(),eq(4L))).thenReturn(0);
        assertThrows(TkTiktokShopApiException.class,()->service.getAccessToken(1L,1L,3L));verify(accounts,never()).markReauthorization(anyLong(),anyLong(),anyLong());
    }
    @Test void staleRefreshFailureOnlyInvalidatesItsOwnVersion() {
        when(accounts.selectById(3L)).thenReturn(account());when(api.refreshToken("refresh")).thenThrow(new TkTiktokShopApiException("105001","r"));
        assertThrows(TkTiktokShopApiException.class,()->service.getAccessToken(1L,1L,3L));verify(accounts).markReauthorization(3L,1L,4L);
    }
    @Test void wrongRefreshIdentityIsRejectedAndLockContentionNeverCallsProvider() throws Exception {
        when(accounts.selectById(3L)).thenReturn(account());when(api.refreshToken("refresh")).thenReturn(bundle("different"));
        assertThrows(TkTiktokShopApiException.class,()->service.getAccessToken(1L,1L,3L));verify(accounts,never()).replaceTokens(any(),anyLong());
        reset(api);when(lock.tryLock(0,TimeUnit.SECONDS)).thenReturn(false);
        assertThrows(TkTiktokShopApiException.class,()->service.getAccessToken(1L,1L,3L));verifyNoInteractions(api);
    }
    @Test void rejectedReadRefreshesAndRetriesOnce() throws Exception {
        TkTiktokShopAccountDO account=account();LocalDateTime now=LocalDateTime.now(clock);
        account.setAccessTokenExpireTime(now.plusHours(1));
        account.setAccessTokenCipher(cipher.encrypt("old",1L,"fake-key","open"));
        when(accounts.selectById(3L)).thenReturn(account);
        when(accounts.expireAccess(3L,1L,4L,now)).thenAnswer(call->{account.setAccessTokenExpireTime(now);return 1;});
        when(api.refreshToken("refresh")).thenReturn(bundle("open"));
        when(accounts.replaceTokens(any(),eq(4L))).thenReturn(1);
        String result=service.read(account,token->{if("old".equals(token))throw new TkTiktokShopApiException("105002","r");return token;});
        assertEquals("new-access",result);
        verify(api).refreshToken("refresh");
        verify(accounts,never()).markReauthorization(anyLong(),anyLong(),anyLong());
    }
    @Test void backgroundRefreshesBeforeNormalRequestWindow() throws Exception {
        TkTiktokShopAccountDO account=account();account.setAccessTokenExpireTime(LocalDateTime.now(clock).plusMinutes(22));
        when(accounts.selectById(3L)).thenReturn(account);
        when(api.refreshToken("refresh")).thenReturn(bundle("open"));
        when(accounts.replaceTokens(any(),eq(4L))).thenReturn(1);
        service.refreshInBackground(1L,1L,3L,30);
        verify(accounts).replaceTokens(any(),eq(4L));
    }
    TkTiktokShopAccountDO account(){TkTiktokShopAccountDO a=new TkTiktokShopAccountDO();a.setId(3L);a.setTenantId(1L);a.setCompanyId(1L);a.setAppKey("fake-key");a.setOpenId("open");a.setAuthStatus("AUTHORIZED");a.setUserType(1);a.setGrantedScopes("[\"creator.video.write\"]");a.setTokenVersion(4L);a.setAccessTokenExpireTime(LocalDateTime.now(clock).minusSeconds(1));a.setRefreshTokenExpireTime(LocalDateTime.now(clock).plusDays(1));a.setRefreshTokenCipher(cipher.encrypt("refresh",1L,"fake-key","open"));return a;}
    TkTiktokShopApiClient.TokenBundle bundle(String open) throws Exception{return TkTiktokShopApiClient.TokenBundle.parse(new ObjectMapper().readTree("{\"access_token\":\"new-access\",\"refresh_token\":\"new-refresh\",\"open_id\":\""+open+"\",\"user_type\":1,\"granted_scopes\":[\"creator.video.write\"],\"access_token_expire_in\":1800003600,\"refresh_token_expire_in\":1800100000}"),clock);}
}

package cn.iocoder.yudao.module.tk.service.tiktokshop;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.tk.dal.dataobject.tiktokshop.TkTiktokShopSellerAccountDO;
import cn.iocoder.yudao.module.tk.dal.mysql.tiktokshop.TkTiktokShopSellerAccountMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.redisson.api.*;
import java.time.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class TkTiktokShopSellerTokenServiceTest {
    TkTiktokShopSellerAccountMapper accounts=mock(TkTiktokShopSellerAccountMapper.class);TkTiktokShopSellerApiClient api=mock(TkTiktokShopSellerApiClient.class);
    RedissonClient redis=mock(RedissonClient.class);RLock lock=mock(RLock.class);
    Clock clock=Clock.fixed(Instant.ofEpochSecond(1800000000),ZoneOffset.UTC);
    TkTiktokShopTokenCipher cipher=new TkTiktokShopTokenCipher(cn.iocoder.yudao.module.tk.framework.config.TkTiktokShopSellerPropertiesTest.configuredSeller());
    TkTiktokShopSellerTokenService service=new TkTiktokShopSellerTokenService(cn.iocoder.yudao.module.tk.framework.config.TkTiktokShopSellerPropertiesTest.configuredSeller(),accounts,api,cipher,redis,clock);
    @BeforeEach void init() throws Exception{TenantContextHolder.setTenantId(1L);when(redis.getLock(anyString())).thenReturn(lock);when(lock.tryLock(0,TimeUnit.SECONDS)).thenReturn(true);when(lock.isHeldByCurrentThread()).thenReturn(true);}
    @AfterEach void clear(){TenantContextHolder.clear();}
    @Test void workerRequiresKnownTenantCompanyAndNoIgnore() {
        when(accounts.selectById(3L)).thenReturn(account());
        assertThrows(IllegalStateException.class,()->service.getAccessToken(2L,1L,3L));
        assertThrows(IllegalStateException.class,()->service.getAccessToken(1L,2L,3L));
        TenantContextHolder.setIgnore(true);assertThrows(IllegalStateException.class,()->service.getAccessToken(1L,1L,3L));
        verifyNoInteractions(api);
    }
    @Test void rotatingRefreshUsesDistributedLockAndVersionCas() throws Exception {
        when(accounts.selectById(3L)).thenReturn(account());when(api.refreshToken("refresh")).thenReturn(bundle("open"));when(accounts.replaceTokens(any(),eq(4L))).thenReturn(1);
        TkTiktokShopSellerTokenService.AccessToken result=service.getAccessToken(1L,1L,3L);
        assertEquals("new-access",result.getValue());assertEquals(5L,result.getVersion());
        verify(lock).tryLock(0,TimeUnit.SECONDS);verify(accounts).replaceTokens(any(),eq(4L));verify(lock).unlock();
        assertFalse(result.toString().contains("new-access"));
    }
    @Test void disconnectOrReauthDuringRefreshCannotBeOverwritten() throws Exception {
        when(accounts.selectById(3L)).thenReturn(account());when(api.refreshToken("refresh")).thenReturn(bundle("open"));when(accounts.replaceTokens(any(),eq(4L))).thenReturn(0);
        assertThrows(IllegalStateException.class,()->service.getAccessToken(1L,1L,3L));verify(accounts,never()).markReauthorization(anyLong(),anyLong(),anyLong());
    }
    @Test void staleRefreshFailureOnlyInvalidatesItsOwnVersion() {
        when(accounts.selectById(3L)).thenReturn(account());when(api.refreshToken("refresh")).thenThrow(new TkTiktokShopApiException("105001","r"));
        assertThrows(TkTiktokShopApiException.class,()->service.getAccessToken(1L,1L,3L));verify(accounts).markReauthorization(3L,1L,4L);
    }
    @Test void businessPermissionDenialKeepsSellerTokensButInvalidTokenRequiresReconnect() {
        when(accounts.selectById(3L)).thenReturn(account());
        service.reportRejectedToken(1L,1L,3L,4L,new TkTiktokShopApiException("105005","denied"));
        verify(accounts,never()).markReauthorization(anyLong(),anyLong(),anyLong());
        service.reportRejectedToken(1L,1L,3L,4L,new TkTiktokShopApiException("105001","expired"));
        verify(accounts).markReauthorization(3L,1L,4L);
    }
    @Test void typedRefreshRejectionRequiresFreshAuthorization() {
        for(String reason:new String[]{"WRONG_ACCOUNT_TYPE","INSUFFICIENT_SCOPE"}){
            reset(api,accounts);when(accounts.selectById(3L)).thenReturn(account());when(api.refreshToken("refresh")).thenThrow(new TkTiktokShopApiException(reason,"request-1"));
            assertEquals(reason,assertThrows(TkTiktokShopApiException.class,()->service.getAccessToken(1L,1L,3L)).getCode());
            verify(accounts).markReauthorization(3L,1L,4L);
        }
    }
    @Test void wrongRefreshIdentityIsRejectedAndLockContentionNeverCallsProvider() throws Exception {
        when(accounts.selectById(3L)).thenReturn(account());when(api.refreshToken("refresh")).thenReturn(bundle("different"));
        assertThrows(TkTiktokShopApiException.class,()->service.getAccessToken(1L,1L,3L));verify(accounts,never()).replaceTokens(any(),anyLong());
        reset(api);when(lock.tryLock(0,TimeUnit.SECONDS)).thenReturn(false);
        assertThrows(IllegalStateException.class,()->service.getAccessToken(1L,1L,3L));verifyNoInteractions(api);
    }
    TkTiktokShopSellerAccountDO account(){TkTiktokShopSellerAccountDO a=new TkTiktokShopSellerAccountDO();a.setId(3L);a.setTenantId(1L);a.setCompanyId(1L);a.setAppKey("seller-key");a.setOpenId("open");a.setAuthStatus("AUTHORIZED");a.setUserType(0);a.setEnvironment("PRODUCTION");a.setGrantedScopes("[\"seller.authorization.info\"]");a.setTokenVersion(4L);a.setAccessTokenExpireTime(LocalDateTime.now(clock).minusSeconds(1));a.setRefreshTokenExpireTime(LocalDateTime.now(clock).plusDays(1));a.setRefreshTokenCipher(cipher.encryptSeller("refresh",1L,"seller-key","PRODUCTION","open"));return a;}
    TkTiktokShopApiClient.TokenBundle bundle(String open) throws Exception{return TkTiktokShopApiClient.TokenBundle.parse(new ObjectMapper().readTree("{\"access_token\":\"new-access\",\"refresh_token\":\"new-refresh\",\"open_id\":\""+open+"\",\"user_type\":0,\"granted_scopes\":[\"seller.authorization.info\"],\"access_token_expire_in\":1800003600,\"refresh_token_expire_in\":1800100000}"),clock,0,TkTiktokShopSellerApiClient.REQUIRED_SCOPE);}
}

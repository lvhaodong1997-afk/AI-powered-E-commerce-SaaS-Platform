package cn.iocoder.yudao.module.tk.service.tiktokshop;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.tk.dal.dataobject.tiktokshop.*;
import cn.iocoder.yudao.module.tk.dal.mysql.tiktokshop.*;
import cn.iocoder.yudao.module.tk.service.scope.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class TkTiktokShopAuthServiceTest {
    final TkTiktokShopAuthSessionMapper sessions=mock(TkTiktokShopAuthSessionMapper.class);
    final TkTiktokShopAccountMapper accounts=mock(TkTiktokShopAccountMapper.class);
    final TkDataScopeService scope=mock(TkDataScopeService.class);
    final TkTiktokShopApiClient api=mock(TkTiktokShopApiClient.class);
    final Clock clock=Clock.fixed(Instant.ofEpochSecond(1800000000),ZoneOffset.UTC);
    TkTiktokShopAuthService auth;
    @BeforeEach void init() {
        PlatformTransactionManager tx=mock(PlatformTransactionManager.class);when(tx.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        auth=new TkTiktokShopAuthService(TkTiktokShopTokenCipherTest.properties(),sessions,accounts,scope,api,new TkTiktokShopTokenCipher(TkTiktokShopTokenCipherTest.properties()),new ObjectMapper(),tx,clock);
        when(sessions.finish(anyLong(),anyLong(),anyString(),any())).thenReturn(1);
        when(scope.getCurrentScope()).thenReturn(new TkUserScope(7L,1L,"TENANT_ADMIN",1L));
        when(scope.getWritableCompanyId(any())).thenReturn(1L);TenantContextHolder.setTenantId(1L);
    }
    @AfterEach void clear(){TenantContextHolder.clear();}
    @Test void stateIsRandomOnlyHashPersistedAndCreatorUrlUsed() {
        doAnswer(i->{((TkTiktokShopAuthSessionDO)i.getArgument(0)).setId(10L);return 1;}).when(sessions).insert(any(TkTiktokShopAuthSessionDO.class));
        String first=auth.createSession(null).getAuthorizationUrl();String second=auth.createSession(null).getAuthorizationUrl();
        assertNotEquals(first,second);assertTrue(first.startsWith("https://shop.tiktok.com/alliance/creator/auth?app_key="));assertFalse(first.contains("service_id"));
        ArgumentCaptor<TkTiktokShopAuthSessionDO> c=ArgumentCaptor.forClass(TkTiktokShopAuthSessionDO.class);verify(sessions,times(2)).insert(c.capture());
        String state=okhttp3.HttpUrl.parse(first).queryParameter("state"); assertEquals(43,state.length());
        assertEquals(TkTiktokShopAuthService.hash(state),c.getAllValues().get(0).getStateHash());
        assertFalse(c.getAllValues().get(0).toString().contains(state));
        assertEquals(LocalDateTime.now(clock).plusMinutes(10),c.getValue().getExpireTime());
    }
    @Test void missingExpiredAndReplayedStateNeverExchangeCode() {
        assertEquals("FAILED",auth.callback("code",null,null).getStatus());
        assertEquals("FAILED",auth.callback("code","short",null).getStatus());
        String state=state();when(sessions.claim(eq(TkTiktokShopAuthService.hash(state)),any())).thenReturn(0);
        assertEquals("FAILED",auth.callback("code",state,null).getStatus());verifyNoInteractions(api);
        verify(sessions,never()).findClaimed(anyString());
    }
    @Test void denialConsumesValidStateAndRestoresAnonymousIgnoreContext() {
        String state=state();TkTiktokShopAuthSessionDO session=session();
        when(sessions.claim(anyString(),any())).thenReturn(1);when(sessions.findClaimed(anyString())).thenReturn(session);
        TenantContextHolder.setTenantId(99L);TenantContextHolder.setIgnore(true);
        assertEquals("FAILED",auth.callback("null",state,"auth_denied").getStatus());
        assertEquals(99L,TenantContextHolder.getTenantId());assertTrue(TenantContextHolder.isIgnore());
        verify(sessions).finish(eq(5L),eq(1L),eq("FAILED"),any());verifyNoInteractions(api);
    }
    @Test void wrongMarketAndExistingDifferentOwnerAreRejected() throws Exception {
        String state=state();when(sessions.claim(anyString(),any())).thenReturn(1);when(sessions.findClaimed(anyString())).thenReturn(session());
        when(api.exchangeCode("code")).thenReturn(bundle());
        when(api.getCreatorProfile("access")).thenReturn(new TkTiktokShopApiClient.CreatorProfile("real-user","GB","creator-profile-id"));
        assertEquals("FAILED",auth.callback("code",state,null).getStatus());verify(accounts,never()).insert(any(TkTiktokShopAccountDO.class));
        when(api.getCreatorProfile("access")).thenReturn(new TkTiktokShopApiClient.CreatorProfile("real-user","US","creator-profile-id"));
        TkTiktokShopAccountDO other=new TkTiktokShopAccountDO();other.setTenantId(1L);other.setCompanyId(2L);other.setCreator("8");
        when(accounts.findIdentity(1L,"fake-key","open")).thenReturn(other);
        assertEquals("FAILED",auth.callback("code",state,null).getStatus());verify(accounts,never()).reauthorize(any(),anyLong());
    }
    @Test void validCallbackPersistsRealProfileWithIndependentEncryptedTokens() throws Exception {
        String state=state();when(sessions.claim(anyString(),any())).thenReturn(1);when(sessions.findClaimed(anyString())).thenReturn(session());
        when(api.exchangeCode("code")).thenReturn(bundle());
        when(api.getCreatorProfile("access")).thenAnswer(i->{assertEquals(1L,TenantContextHolder.getTenantId());assertFalse(TenantContextHolder.isIgnore());return new TkTiktokShopApiClient.CreatorProfile("real-user","US","creator-profile-id");});
        assertEquals("SUCCESS",auth.callback("code",state,null).getStatus());
        ArgumentCaptor<TkTiktokShopAccountDO> a=ArgumentCaptor.forClass(TkTiktokShopAccountDO.class);verify(accounts).insert(a.capture());
        assertEquals("real-user",a.getValue().getUsername());assertEquals("open",a.getValue().getOpenId());assertEquals("7",a.getValue().getCreator());assertEquals(1L,a.getValue().getTokenVersion());
        assertNotEquals("access",a.getValue().getAccessTokenCipher());
    }
    @Test void explicitTenantCompanyAndCreatorGuardBeforeDisconnect() {
        TkTiktokShopAccountDO account=new TkTiktokShopAccountDO();account.setId(3L);account.setTenantId(2L);account.setCompanyId(1L);account.setCreator("7");
        account.setAppKey("fake-key");when(accounts.selectById(3L)).thenReturn(account);assertThrows(IllegalArgumentException.class,()->auth.disconnect(3L));
        account.setTenantId(1L);account.setCompanyId(2L);assertThrows(IllegalArgumentException.class,()->auth.disconnect(3L));
        verify(accounts,never()).disconnect(anyLong(),anyLong());
    }
    static String state(){return "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG";}
    static TkTiktokShopAuthSessionDO session(){TkTiktokShopAuthSessionDO s=new TkTiktokShopAuthSessionDO();s.setId(5L);s.setTenantId(1L);s.setCompanyId(1L);s.setUserId(7L);s.setCreator("7");return s;}
    TkTiktokShopApiClient.TokenBundle bundle() throws Exception{return TkTiktokShopApiClient.TokenBundle.parse(new ObjectMapper().readTree("{\"access_token\":\"access\",\"refresh_token\":\"refresh\",\"open_id\":\"open\",\"user_type\":1,\"granted_scopes\":[\"creator.video.write\"],\"access_token_expire_in\":1800003600,\"refresh_token_expire_in\":1800100000}"),clock);}
}

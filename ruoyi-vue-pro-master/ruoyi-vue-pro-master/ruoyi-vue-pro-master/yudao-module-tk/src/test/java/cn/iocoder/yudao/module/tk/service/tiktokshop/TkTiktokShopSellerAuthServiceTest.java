package cn.iocoder.yudao.module.tk.service.tiktokshop;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.tk.dal.dataobject.tiktokshop.*;
import cn.iocoder.yudao.module.tk.dal.mysql.tiktokshop.*;
import cn.iocoder.yudao.module.tk.framework.config.*;
import cn.iocoder.yudao.module.tk.service.scope.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class TkTiktokShopSellerAuthServiceTest {
    TkTiktokShopProperties p=TkTiktokShopSellerPropertiesTest.configuredSeller();
    TkTiktokShopAuthSessionMapper sessions=mock(TkTiktokShopAuthSessionMapper.class);
    TkTiktokShopAccountMapper creators=mock(TkTiktokShopAccountMapper.class);
    TkTiktokShopSellerAccountMapper accounts=mock(TkTiktokShopSellerAccountMapper.class);
    TkTiktokShopSellerShopMapper shops=mock(TkTiktokShopSellerShopMapper.class);
    TkDataScopeService scope=mock(TkDataScopeService.class);
    TkTiktokShopApiClient creatorApi=mock(TkTiktokShopApiClient.class);
    TkTiktokShopSellerApiClient api=mock(TkTiktokShopSellerApiClient.class);
    Clock clock=Clock.fixed(Instant.ofEpochSecond(1800000000),ZoneOffset.UTC);
    TkTiktokShopSellerAuthService seller;
    TkTiktokShopAuthService auth;
    TkTiktokShopAuthSessionDO saved;
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);when(scope.getCurrentScope()).thenReturn(new TkUserScope(7L,1L,"TENANT_ADMIN",1L));when(scope.getWritableCompanyId(any())).thenReturn(1L);
        TkTiktokShopTokenCipher cipher=new TkTiktokShopTokenCipher(p);seller=new TkTiktokShopSellerAuthService(p,accounts,shops,scope,api,cipher);
        PlatformTransactionManager tx=mock(PlatformTransactionManager.class);when(tx.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        auth=new TkTiktokShopAuthService(p,sessions,creators,scope,creatorApi,cipher,new ObjectMapper(),tx,clock);ReflectionTestUtils.setField(auth,"sellerAuth",seller);
        doAnswer(i->{saved=i.getArgument(0);saved.setId(5L);return 1;}).when(sessions).insert(any(TkTiktokShopAuthSessionDO.class));
        when(sessions.claim(anyString(),any())).thenReturn(1);when(sessions.findClaimed(anyString())).thenAnswer(i->saved);when(sessions.finish(anyLong(),anyLong(),anyString(),any())).thenReturn(1);
        doAnswer(i->{((TkTiktokShopSellerAccountDO)i.getArgument(0)).setId(10L);return 1;}).when(accounts).insert(any(TkTiktokShopSellerAccountDO.class));
        when(shops.findByAccount(anyLong(),anyLong())).thenReturn(Collections.emptyList());
    }
    @AfterEach void clear(){TenantContextHolder.clear();}
    String start(){return okhttp3.HttpUrl.parse(auth.createSession(1L,"SELLER").getAuthorizationUrl()).queryParameter("state");}
    TkTiktokShopApiClient.TokenBundle token() throws Exception {return TkTiktokShopApiClient.TokenBundle.parse(new ObjectMapper().readTree("{\"access_token\":\"access\",\"refresh_token\":\"refresh\",\"open_id\":\"seller-open\",\"user_type\":0,\"granted_scopes\":[\"seller.authorization.info\"],\"access_token_expire_in\":1800003600,\"refresh_token_expire_in\":1800100000}"),clock,0,TkTiktokShopSellerApiClient.REQUIRED_SCOPE);}
    @Test void storedSellerTypeWorksWithCreatorDisabledAndNeverCallsCreator() throws Exception {
        String state=start();assertEquals("SELLER",saved.getAuthType());assertEquals("seller-key",saved.getAppKey());assertEquals("PRODUCTION",saved.getEnvironment());assertNotNull(saved.getConfigurationHash());
        when(api.exchangeCode("secret-code")).thenReturn(token());when(api.getAuthorizedShops("access")).thenReturn(List.of(new TkTiktokShopSellerApiClient.AuthorizedShop("s1","Store","US","private-cipher","LOCAL","code")));
        assertEquals("SUCCESS",auth.callback("secret-code",state,null).getStatus());verifyNoInteractions(creatorApi,creators);verify(accounts).insert(any(TkTiktokShopSellerAccountDO.class));verify(shops).insert(any(TkTiktokShopSellerShopDO.class));
    }
    @Test void changedAppSecretEnvironmentOrAuthorizationUrlClosesClaimWithoutExchange() {
        for(int i=0;i<3;i++){p=TkTiktokShopSellerPropertiesTest.configuredSeller();setup();String state=start();if(i==0)p.getSeller().setAppSecret("changed");if(i==1)p.getSeller().setEnvironment("TEST");if(i==2)p.getSeller().setAuthorizationUrl("https://services.tiktokshop.com/open/authorize?service_id=other");
            assertEquals("CONFIGURATION_CHANGED",auth.callback("code",state,null).getErrorCode());verify(sessions).recordFailure(5L,1L,"CONFIGURATION_CHANGED");verifyNoInteractions(api,creatorApi);reset(sessions);}
    }
    @Test void newCreatorSessionCannotBypassSnapshotValidationByLosingFingerprint() throws Exception {
        p.setEnabled(true);p.setAppKey("creator-key");p.setAppSecret("creator-secret");
        String state=okhttp3.HttpUrl.parse(auth.createSession(1L,"CREATOR").getAuthorizationUrl()).queryParameter("state");saved.setConfigurationHash(null);
        when(creatorApi.exchangeCode("code")).thenReturn(new TkTiktokShopAuthServiceTest().bundle());when(creatorApi.getCreatorProfile("access")).thenReturn(new TkTiktokShopApiClient.CreatorProfile("user","US","open"));
        assertEquals("FAILED",auth.callback("code",state,null).getStatus());verify(creators,never()).insert(any(TkTiktokShopAccountDO.class));
    }
    @Test void unknownTypesAndForgedSellerSessionSnapshotsAreRejected() {
        assertThrows(IllegalArgumentException.class,()->auth.createSession(null,"seller"));String state=start();saved.setConfigurationHash(null);assertEquals("FAILED",auth.callback("code",state,null).getStatus());verifyNoInteractions(api);
    }
    @Test void providerSecretsAreNeverSavedOrReturnedInCallbackFailure() {
        String state=start();when(api.exchangeCode(anyString())).thenThrow(new RuntimeException("raw-secret-code access-token https://private"));
        cn.iocoder.yudao.module.tk.controller.admin.tiktokshop.vo.TkTiktokShopAuthVO.CallbackResult result=auth.callback("raw-secret-code",state,null);assertEquals("AUTHORIZATION_FAILED",result.getErrorCode());assertFalse(result.getMessage().contains("raw-secret"));verify(sessions).recordFailure(5L,1L,"AUTHORIZATION_FAILED");
    }
    @Test void removedShopsAreInvalidatedAndExistingIdsRetainedOnReauthorization() throws Exception {
        start();TkTiktokShopSellerAccountDO a=account();when(accounts.findIdentity(1L,"seller-key","PRODUCTION","seller-open")).thenReturn(a);when(accounts.reauthorize(any(),eq(2L))).thenReturn(1);
        TkTiktokShopSellerShopDO old=shop(20L,"kept");when(shops.findByAccount(1L,10L)).thenReturn(List.of(old,shop(21L,"removed")));
        seller.bind(saved,token(),List.of(new TkTiktokShopSellerApiClient.AuthorizedShop("kept","New name","US","new-cipher","LOCAL","code")));
        verify(shops).invalidate(1L,10L);verify(shops).updateById(argThat((TkTiktokShopSellerShopDO s)->s.getId()==20L && "AUTHORIZED".equals(s.getStatus()) && "new-cipher".equals(s.getShopCipher())));verify(shops,never()).insert(any(TkTiktokShopSellerShopDO.class));
    }
    @Test void differentOwnerCannotRebindSellerIdentity() throws Exception {
        start();TkTiktokShopSellerAccountDO a=account();a.setCompanyId(2L);when(accounts.findIdentity(anyLong(),anyString(),anyString(),anyString())).thenReturn(a);
        assertThrows(IllegalArgumentException.class,()->seller.bind(saved,token(),Collections.emptyList()));verify(shops,never()).invalidate(anyLong(),anyLong());
    }
    @Test void scopedShopLookupRejectsCrossTenantCompanyEnvironmentOrUnavailableShop() {
        TkTiktokShopSellerAccountDO a=account();TkTiktokShopSellerShopDO s=shop(20L,"shop");when(accounts.selectById(10L)).thenReturn(a);when(shops.selectById(20L)).thenReturn(s);
        assertEquals(20L,seller.requireReadableShop(20L).getId());s.setTenantId(2L);assertThrows(IllegalArgumentException.class,()->seller.requireReadableShop(20L));s.setTenantId(1L);s.setEnvironment("TEST");assertThrows(IllegalArgumentException.class,()->seller.requireReadableShop(20L));s.setEnvironment("PRODUCTION");s.setStatus("UNAVAILABLE");assertThrows(IllegalArgumentException.class,()->seller.requireReadableShop(20L));a.setCompanyId(2L);assertThrows(IllegalArgumentException.class,()->seller.requireReadable(10L));
    }
    TkTiktokShopSellerAccountDO account(){TkTiktokShopSellerAccountDO a=new TkTiktokShopSellerAccountDO();a.setId(10L);a.setTenantId(1L);a.setCompanyId(1L);a.setCreator("7");a.setAppKey("seller-key");a.setEnvironment("PRODUCTION");a.setOpenId("seller-open");a.setAuthStatus("AUTHORIZED");a.setTokenVersion(2L);return a;}
    TkTiktokShopSellerShopDO shop(Long id,String remote){TkTiktokShopSellerShopDO s=new TkTiktokShopSellerShopDO();s.setId(id);s.setAccountId(10L);s.setTenantId(1L);s.setCompanyId(1L);s.setEnvironment("PRODUCTION");s.setShopId(remote);s.setStatus("AUTHORIZED");s.setShopCipher("private");return s;}
}

package cn.iocoder.yudao.module.tk.service.social.auth;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.module.tk.dal.dataobject.social.*;
import cn.iocoder.yudao.module.tk.dal.mysql.social.*;
import cn.iocoder.yudao.module.tk.service.scope.*;
import cn.iocoder.yudao.module.tk.service.social.platform.TkSocialPlatformClient;
import cn.iocoder.yudao.module.tk.service.social.platform.TkSocialPlatformException;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TkSocialAuthSecurityTest {
    private final TkSocialProperties properties = new TkSocialProperties();
    private final TkSocialAuthSessionMapper sessions = mock(TkSocialAuthSessionMapper.class);
    private final TkSocialAccountMapper accounts = mock(TkSocialAccountMapper.class);
    private final TkDataScopeService scope = mock(TkDataScopeService.class);
    private final TkSocialPlatformClient platform = mock(TkSocialPlatformClient.class);
    private TkSocialTokenCipher cipher;

    @BeforeEach void setup() {
        properties.setEnabled(true);
        properties.setEncryptionKey(Base64.getEncoder().encodeToString(new byte[32]));
        properties.getInstagram().setAppId("ig-app");
        properties.getInstagram().setAppSecret("configured-app-secret");
        properties.getInstagram().setRedirectUri("https://app.example/instagram/callback");
        cipher = new TkSocialTokenCipher(properties);
        TenantContextHolder.setTenantId(9L);
        when(scope.getCurrentScope()).thenReturn(new TkUserScope(7L, 9L, "TENANT_ADMIN", 9L));
    }
    @AfterEach void cleanup() { TenantContextHolder.clear(); SecurityContextHolder.clearContext(); }

    @Test void cipherAuthenticatesOwnerContextAndUsesFreshNonce() {
        String a = cipher.encrypt("secret", "tenant:9");
        String b = cipher.encrypt("secret", "tenant:9");
        assertNotEquals(a, b); assertEquals("secret", cipher.decrypt(a, "tenant:9"));
        assertThrows(RuntimeException.class, () -> cipher.decrypt(a, "tenant:10"));
        assertThrows(RuntimeException.class, () -> cipher.decrypt(a.substring(0, a.length()-3) + "abc", "tenant:9"));
    }

    @Test void callbackRejectsReplayBeforeAnyTokenExchange() {
        TkSocialAuthService auth = new TkSocialAuthService(properties, sessions, scope, platform, cipher, mock(TkSocialAccountService.class));
        TkSocialAuthSessionDO session = session();
        when(sessions.findByStateHash(anyString())).thenReturn(session);
        when(sessions.claim(eq(session.getId()), eq("PENDING"), eq("PROCESSING"), any())).thenReturn(0);
        assertFalse(auth.callback("INSTAGRAM", "code", "state", null));
        verifyNoInteractions(platform);
    }

    @Test void callbackPersistsActionableReasonAndLogsOnlySafeDiagnosticFields() {
        TkSocialAuthService auth = new TkSocialAuthService(properties, sessions, scope, platform, cipher,
                mock(TkSocialAccountService.class));
        TkSocialAuthSessionDO session = session();
        when(sessions.findByStateHash(anyString())).thenReturn(session);
        when(sessions.claim(eq(session.getId()), eq("PENDING"), eq("PROCESSING"), any())).thenReturn(1);
        TkSocialPlatformException failure=new TkSocialPlatformException("HTTP_400","provider-response-secret",
                false,false,false).withStage("INSTAGRAM_LONG_TOKEN");
        when(platform.authorizeInstagram(eq("callback-code-secret"),anyString())).thenThrow(failure);
        ch.qos.logback.classic.Logger logger=(ch.qos.logback.classic.Logger)
                org.slf4j.LoggerFactory.getLogger(TkSocialAuthService.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> captured=
                new ch.qos.logback.core.read.ListAppender<>();
        captured.start(); logger.addAppender(captured);
        try {
            assertFalse(auth.callback("INSTAGRAM","callback-code-secret","state",null));
            verify(sessions).finish(eq(session.getId()),eq("PROCESSING"),eq("FAILED"),isNull(),
                    eq("Instagram 长效令牌获取失败，请重新授权"),any());
            String logs=captured.list.toString();
            assertTrue(logs.contains("INSTAGRAM_LONG_TOKEN"));
            assertTrue(logs.contains("HTTP_400"));
            assertTrue(logs.contains("TkSocialPlatformException"));
            assertFalse(logs.contains("callback-code-secret"));
            assertFalse(logs.contains("provider-response-secret"));
            assertFalse(logs.contains("configured-app-secret"));
        } finally { logger.detachAppender(captured); captured.stop(); }
    }

    @Test void sessionIsUnreadableByDifferentUserEvenWithinTenant() {
        TkSocialAuthService auth = new TkSocialAuthService(properties, sessions, scope, platform, cipher, mock(TkSocialAccountService.class));
        TkSocialAuthSessionDO session = session(); session.setCreator("8");
        when(sessions.findBySessionId("s")).thenReturn(session);
        assertThrows(RuntimeException.class, () -> auth.session("s"));
    }

    @Test void sessionExpiredOrWrongPlatformCannotExchangeCode() {
        TkSocialAuthService auth = new TkSocialAuthService(properties, sessions, scope, platform, cipher, mock(TkSocialAccountService.class));
        TkSocialAuthSessionDO session = session(); session.setExpireTime(LocalDateTime.now().minusSeconds(1));
        when(sessions.findByStateHash(anyString())).thenReturn(session);
        assertFalse(auth.callback("INSTAGRAM", "code", "state", null));
        session.setExpireTime(LocalDateTime.now().plusMinutes(1));
        assertFalse(auth.callback("FACEBOOK_PAGE", "code", "state", null));
        verifyNoInteractions(platform);
    }

    @Test void reauthorizationCannotTakeOverAnExistingAccount() {
        TkSocialAccountService service = new TkSocialAccountService(properties, accounts, scope, platform, cipher);
        TkSocialAccountDO existing = new TkSocialAccountDO();
        existing.setTenantId(9L); existing.setCompanyId(9L); existing.setCreator("8");
        assertThrows(RuntimeException.class, () -> service.checkBindingOwner(existing, session()));
    }

    @Test void backgroundTokenUseRequiresExplicitMatchingTenant() {
        TkSocialAccountService service = new TkSocialAccountService(properties, accounts, scope, platform, cipher);
        TkSocialAccountDO account = new TkSocialAccountDO();
        account.setTenantId(10L); account.setStatus("AUTHORIZED");
        assertThrows(RuntimeException.class, () -> service.getValidToken(account));
        verifyNoInteractions(platform);
    }

    @Test void insightsUseStoredAccountTokenAndDoNotAcceptCallerToken() throws Exception {
        TkSocialAccountService service = new TkSocialAccountService(properties, accounts, scope, platform, cipher);
        TkSocialAccountDO account = authorizedAccount("stored-token");
        when(accounts.selectById(1L)).thenReturn(account);
        com.fasterxml.jackson.databind.JsonNode response = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree("{\"data\":[{\"name\":\"reach\"}]}");
        when(platform.instagramInsights("42", "stored-token", "reach", "day")).thenReturn(response);

        assertSame(response, service.insights(1L, "reach", "day"));
        verify(platform).instagramInsights("42", "stored-token", "reach", "day");
        verify(platform, never()).instagramInsights(anyString(), eq("caller-token"), anyString(), anyString());
    }

    @Test void insightsUsesSelectedTenantWhenPlatformAdminHasNoThreadTenant() throws Exception {
        TkSocialAccountService service = new TkSocialAccountService(properties, accounts, scope, platform, cipher);
        TkSocialAccountDO account = authorizedAccount("stored-token");
        when(accounts.selectById(1L)).thenReturn(account);
        com.fasterxml.jackson.databind.JsonNode response = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree("{\"data\":[{\"name\":\"reach\"}]}");
        when(platform.instagramInsights("42", "stored-token", "reach", "day")).thenReturn(response);
        when(scope.getCurrentScope()).thenReturn(new TkUserScope(7L, 9L, "PLATFORM_ADMIN", 9L));

        LoginUser loginUser=new LoginUser().setId(7L).setTenantId(1L).setVisitTenantId(9L);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(loginUser,null));
        TenantContextHolder.clear();

        assertSame(response, service.insights(1L, "reach", "day"));
        verify(platform).instagramInsights("42", "stored-token", "reach", "day");
    }

    @Test void disabledFeatureNeverQueriesMissingTables() {
        properties.setEnabled(false);
        TkSocialAccountService service = new TkSocialAccountService(properties, accounts, scope, platform, cipher);
        assertThrows(RuntimeException.class, () -> service.requireReadable(1L));
        verifyNoInteractions(accounts);
    }

    @Test void safeAccountResponseCannotSerializeTokenOrProviderUser() throws Exception {
        TkSocialAccountDO account=new TkSocialAccountDO();
        account.setAccessTokenCiphertext("private-cipher"); account.setProviderUserId("private-provider");
        String encoded=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(TkSocialAccountService.safeAccount(account));
        assertFalse(encoded.contains("private-cipher")); assertFalse(encoded.contains("private-provider"));
        assertFalse(encoded.contains("accessToken")); assertFalse(encoded.contains("providerUser"));
    }

    @Test void facebookBindingRejectsForgedPageIdBeforeAnyPersistence() throws Exception {
        TkSocialAuthService auth=new TkSocialAuthService(properties,sessions,scope,platform,cipher,mock(TkSocialAccountService.class));
        TkSocialAuthSessionDO session=session(); session.setPlatform("FACEBOOK_PAGE"); session.setStatus("PAGES_READY");
        TkSocialPlatformClient.Authorization authorization=new TkSocialPlatformClient.Authorization();
        TkSocialPlatformClient.PageCandidate page=new TkSocialPlatformClient.PageCandidate();
        page.setId("1"); page.setName("allowed"); page.setTasks(Arrays.asList("CREATE_CONTENT")); page.setAccessToken("private");
        authorization.setPages(Arrays.asList(page));
        session.setPayloadCiphertext(cipher.encrypt(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(authorization),
                TkSocialAuthService.sessionContext(session)));
        when(sessions.findBySessionId("s")).thenReturn(session);
        assertThrows(IllegalArgumentException.class,()->auth.bindFacebookPages("s",Arrays.asList("2")));
        verify(sessions,never()).claim(any(),anyString(),anyString(),any());
        assertFalse(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(auth.facebookPages("s")).contains("private"));
    }

    @Test void facebookPageBindingUsesAuthorizationTenantInsideTransaction() throws Exception {
        TkSocialAuthSessionDO session=session(); session.setPlatform("FACEBOOK_PAGE"); session.setStatus("PAGES_READY");
        TkSocialPlatformClient.Authorization authorization=new TkSocialPlatformClient.Authorization();
        authorization.setExternalId("fb-user"); authorization.setScopes("pages_manage_posts");
        TkSocialPlatformClient.PageCandidate page=new TkSocialPlatformClient.PageCandidate();
        page.setId("1"); page.setName("page"); page.setTasks(Arrays.asList("CREATE_CONTENT")); page.setAccessToken("page-token");
        authorization.setPages(Arrays.asList(page));
        session.setPayloadCiphertext(cipher.encrypt(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(authorization),
                TkSocialAuthService.sessionContext(session)));
        when(sessions.findBySessionId("s")).thenReturn(session);
        when(sessions.claim(eq(session.getId()),eq("PAGES_READY"),eq("PROCESSING"),any())).thenReturn(1);
        when(sessions.finish(eq(session.getId()),eq("PROCESSING"),eq("SUCCESS"),isNull(),isNull(),any())).thenReturn(1);
        when(accounts.findExternal(9L,"FACEBOOK_PAGE","1")).thenReturn(null);

        PlatformTransactionManager transactionManager=mock(PlatformTransactionManager.class);
        TransactionStatus transactionStatus=mock(TransactionStatus.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
        TkSocialAuthService auth=new TkSocialAuthService(properties,sessions,scope,platform,cipher,
                new TkSocialAccountService(properties,accounts,scope,platform,cipher));
        ReflectionTestUtils.setField(auth,"transactionManager",transactionManager);

        TenantContextHolder.clear();
        assertDoesNotThrow(() -> auth.bindFacebookPages("s",Arrays.asList("1")));
        verify(accounts).insert(any(TkSocialAccountDO.class));
        verify(transactionManager).commit(transactionStatus);
        assertNull(TenantContextHolder.getTenantId());
    }

    @Test void deletedAccountRebindReusesOriginalRowWithoutChangingOwner() {
        TkSocialAccountService service=new TkSocialAccountService(properties,accounts,scope,platform,cipher);
        TkSocialAccountDO existing=new TkSocialAccountDO();
        existing.setId(30L); existing.setTenantId(9L); existing.setCompanyId(9L); existing.setCreator("7");
        existing.setPlatform("INSTAGRAM"); existing.setExternalAccountId("42"); existing.setStatus("DELETED");
        when(accounts.findExternal(9L,"INSTAGRAM","42")).thenReturn(existing);
        TkSocialPlatformClient.Authorization authorization=new TkSocialPlatformClient.Authorization();
        authorization.setExternalId("42"); authorization.setAccessToken("new-token");
        authorization.setExpiresAt(LocalDateTime.now().plusDays(60));
        service.bind(session(),authorization,Collections.emptyList());
        assertEquals(30L,existing.getId()); assertEquals("7",existing.getCreator());
        assertEquals("AUTHORIZED",existing.getStatus());
        assertEquals("new-token",cipher.decrypt(existing.getAccessTokenCiphertext(),TkSocialAccountService.accountContext(existing)));
        verify(accounts,never()).insert(any(TkSocialAccountDO.class)); verify(accounts).updateById(existing);
    }

    @Test void instagramBindingStoresPublishingAndProviderIdentitiesSeparately() {
        TkSocialAccountService service=new TkSocialAccountService(properties,accounts,scope,platform,cipher);
        TkSocialPlatformClient.Authorization authorization=new TkSocialPlatformClient.Authorization();
        authorization.setExternalId("200"); authorization.setProviderUserId("100");
        authorization.setAccessToken("new-token"); authorization.setExpiresAt(LocalDateTime.now().plusDays(60));

        service.bind(session(),authorization,Collections.emptyList());

        org.mockito.ArgumentCaptor<TkSocialAccountDO> inserted=org.mockito.ArgumentCaptor.forClass(TkSocialAccountDO.class);
        verify(accounts).insert(inserted.capture());
        assertEquals("200",inserted.getValue().getExternalAccountId());
        assertEquals("100",inserted.getValue().getProviderUserId());
        assertEquals(session().getCreator(),inserted.getValue().getUpdater());
    }

    @Test void instagramIdentityMismatchUsesAccurateSafeFailureReason() {
        TkSocialAuthService auth=new TkSocialAuthService(properties,sessions,scope,platform,cipher,
                mock(TkSocialAccountService.class));
        TkSocialAuthSessionDO session=session();
        when(sessions.findByStateHash(anyString())).thenReturn(session);
        when(sessions.claim(eq(session.getId()),eq("PENDING"),eq("PROCESSING"),any())).thenReturn(1);
        when(platform.authorizeInstagram(eq("code"),anyString())).thenThrow(
                new TkSocialPlatformException("IG_IDENTITY_MISMATCH","private-provider-message",false,false,false)
                        .withStage("INSTAGRAM_PROFILE"));

        assertFalse(auth.callback("INSTAGRAM","code","state",null));

        verify(sessions).finish(eq(session.getId()),eq("PROCESSING"),eq("FAILED"),isNull(),
                eq("Instagram 授权身份校验失败，请重新授权"),any());
    }

    @Test void expiredInstagramTokenDoesNotAttemptRefresh() {
        TkSocialAccountService service=new TkSocialAccountService(properties,accounts,scope,platform,cipher);
        TkSocialAccountDO account=new TkSocialAccountDO();
        account.setId(1L); account.setTenantId(9L); account.setExternalAccountId("42"); account.setPlatform("INSTAGRAM");
        account.setStatus("AUTHORIZED"); account.setTokenExpiresAt(LocalDateTime.now().minusSeconds(1));
        when(accounts.selectById(1L)).thenReturn(account);
        assertThrows(RuntimeException.class,()->service.getValidToken(account));
        verifyNoInteractions(platform);
    }

    @Test void refreshLosingToUnbindCannotReturnOrRestoreNewToken() {
        TkSocialAccountService service=new TkSocialAccountService(properties,accounts,scope,platform,cipher);
        TkSocialAccountDO account=new TkSocialAccountDO();
        account.setId(1L); account.setTenantId(9L); account.setExternalAccountId("42"); account.setPlatform("INSTAGRAM");
        account.setStatus("AUTHORIZED"); account.setLastAuthTime(LocalDateTime.now().minusDays(55));
        account.setTokenExpiresAt(LocalDateTime.now().plusDays(5));
        account.setAccessTokenCiphertext(cipher.encrypt("old-token",TkSocialAccountService.accountContext(account)));
        when(accounts.selectById(1L)).thenReturn(account);
        TkSocialPlatformClient.Authorization refreshed=new TkSocialPlatformClient.Authorization();
        refreshed.setAccessToken("renewed"); refreshed.setExpiresAt(LocalDateTime.now().plusDays(60));
        when(platform.refreshInstagram("old-token")).thenReturn(refreshed);
        when(accounts.replaceToken(eq(1L),eq(9L),eq(account.getAccessTokenCiphertext()),anyString(),any(),any())).thenReturn(0);
        assertThrows(IllegalStateException.class,()->service.getValidToken(account));
        verify(accounts,never()).updateById(any(TkSocialAccountDO.class));
        assertEquals("old-token",cipher.decrypt(account.getAccessTokenCiphertext(),TkSocialAccountService.accountContext(account)));
    }

    @Test void rejectionOfOldTokenCannotInvalidateConcurrentReauthorization() {
        TkSocialAccountService service=new TkSocialAccountService(properties,accounts,scope,platform,cipher);
        TkSocialAccountDO latest=authorizedAccount("new-token");
        when(accounts.selectById(1L)).thenReturn(latest);
        service.reportRejectedToken(1L,"old-token");
        verify(accounts,never()).markReauth(any(),any(),anyString(),anyString(),any());
        assertEquals("AUTHORIZED",latest.getStatus());
    }

    @Test void rejectionOfCurrentTokenUsesTenantAndCipherCompareAndSet() {
        TkSocialAccountService service=new TkSocialAccountService(properties,accounts,scope,platform,cipher);
        TkSocialAccountDO current=authorizedAccount("rejected-token");
        when(accounts.selectById(1L)).thenReturn(current);
        service.reportRejectedToken(1L,"rejected-token");
        verify(accounts).markReauth(eq(1L),eq(9L),eq(current.getAccessTokenCiphertext()),anyString(),any());
        verify(accounts,never()).updateById(any(TkSocialAccountDO.class));
    }

    @Test void tokenRejectionRequiresExplicitNonIgnoredTenant() {
        TkSocialAccountService service=new TkSocialAccountService(properties,accounts,scope,platform,cipher);
        TenantContextHolder.clear();
        assertThrows(IllegalStateException.class,()->service.reportRejectedToken(1L,"token"));
        TenantContextHolder.setTenantId(9L); TenantContextHolder.setIgnore(true);
        assertThrows(IllegalStateException.class,()->service.reportRejectedToken(1L,"token"));
        verifyNoInteractions(accounts);
    }

    @Test void rejectedTokenDoesNotTouchDifferentTenantOrUnboundAccount() {
        TkSocialAccountService service=new TkSocialAccountService(properties,accounts,scope,platform,cipher);
        TkSocialAccountDO current=authorizedAccount("token");
        current.setTenantId(10L); when(accounts.selectById(1L)).thenReturn(current);
        service.reportRejectedToken(1L,"token");
        current.setTenantId(9L); current.setStatus("UNBOUND");
        service.reportRejectedToken(1L,"token");
        verify(accounts,never()).markReauth(any(),any(),anyString(),anyString(),any());
    }

    private TkSocialAccountDO authorizedAccount(String token) {
        TkSocialAccountDO account=new TkSocialAccountDO();
        account.setId(1L); account.setTenantId(9L); account.setExternalAccountId("42"); account.setPlatform("INSTAGRAM");
        account.setStatus("AUTHORIZED");
        account.setAccessTokenCiphertext(cipher.encrypt(token,TkSocialAccountService.accountContext(account)));
        return account;
    }

    private TkSocialAuthSessionDO session() {
        TkSocialAuthSessionDO s = new TkSocialAuthSessionDO();
        s.setId(1L); s.setTenantId(9L); s.setCompanyId(9L); s.setCreator("7");
        s.setSessionId("s"); s.setPlatform("INSTAGRAM"); s.setStatus("PENDING");
        s.setExpireTime(LocalDateTime.now().plusMinutes(10)); return s;
    }
}

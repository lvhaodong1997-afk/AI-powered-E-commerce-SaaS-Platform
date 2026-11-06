package cn.iocoder.yudao.module.tk.service.open.tiktok;

import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenTiktokAuthSessionDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenTiktokConnectionDO;
import cn.iocoder.yudao.module.tk.controller.open.tiktok.vo.TkOpenTiktokAuthVO;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenTiktokAuthSessionMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenTiktokConnectionMapper;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiSecretCipher;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiContext;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiPrincipal;
import cn.iocoder.yudao.module.tk.service.open.api.TkOpenApiCallbackService;
import cn.iocoder.yudao.module.tk.service.open.platform.TkOpenPublishPlatformAdapter;
import cn.iocoder.yudao.module.tk.service.open.platform.TkOpenPublishPlatformRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.mockito.ArgumentCaptor;

import javax.validation.constraints.Pattern;

import java.time.LocalDateTime;
import java.time.Instant;
import java.time.ZoneId;
import java.util.TimeZone;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class TkOpenTiktokAuthServiceTest {

    @AfterEach
    void clearContext() {
        TkOpenApiContext.clear();
    }

    @ParameterizedTest
    @CsvSource({"confirmed,", "scanned,", "confirmed, ''", "confirmed, ' '",
            "scanned, ''", "scanned, ' '", "confirmed, other", "scanned, other"})
    void shouldRejectMissingOrMismatchedTicketBeforeCodeExchange(String status, String ticket) {
        QrFixture fixture = new QrFixture(status, ticket);
        TkOpenTiktokAuthVO.SessionStatusResp response = fixture.service.getSession("auth_qr");
        assertEquals("FAILED", response.getStatus());
        assertTrue(response.getFailReason().contains("client_ticket"));
        verify(fixture.adapter, never()).exchangeCode(any(), any());
        verifyNoInteractions(fixture.connectionMapper, fixture.cipher);
    }

    @ParameterizedTest
    @ValueSource(strings = {"new", "expired"})
    void shouldAllowEmptyTicketForInitialOrExpiredQr(String status) {
        QrFixture fixture = new QrFixture(status, null);
        TkOpenTiktokAuthVO.SessionStatusResp response = fixture.service.getSession("auth_qr");
        assertEquals("expired".equals(status) ? "EXPIRED" : "WAITING", response.getStatus());
        verify(fixture.adapter, never()).exchangeCode(any(), any());
        verifyNoInteractions(fixture.callbacks);
    }

    @Test
    void shouldCompleteConfirmedQrWithMatchingTicket() {
        QrFixture fixture = new QrFixture("confirmed", "ticket-expected");
        when(fixture.adapter.exchangeCode("qr-code", null)).thenReturn(
                new TkOpenPublishPlatformAdapter.OAuthTokenResult(true, "access", "refresh",
                        3600L, 7200L, "user.info.basic", "open-id", null, null));
        when(fixture.adapter.queryUserInfo("access")).thenReturn(
                new TkOpenPublishPlatformAdapter.PlatformUser(true, "open-id", "Name", "user", null, null));
        TkOpenTiktokAuthVO.SessionStatusResp response = fixture.service.getSession("auth_qr");
        assertEquals("SUCCESS", response.getStatus());
        verify(fixture.adapter).exchangeCode("qr-code", null);
        verify(fixture.connectionMapper).insert(any(TkOpenTiktokConnectionDO.class));
        verify(fixture.callbacks).enqueue(eq("client_a"), eq("authorization.completed"), eq("CONNECTION"), any(), any());
    }

    @Test
    void shouldExposeUnambiguousExpiryInNonUtcTimezone() throws Exception {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
            QrFixture fixture = new QrFixture("new", null);
            fixture.session.setExpireTime(LocalDateTime.now().plusSeconds(120));
            TkOpenTiktokAuthVO.SessionStatusResp response = fixture.service.getSession("auth_qr");
            Map<?, ?> json = new ObjectMapper().findAndRegisterModules().convertValue(response, Map.class);
            assertTrue(json.containsKey("expiresAt"));
            assertEquals(fixture.session.getExpireTime().atZone(ZoneId.systemDefault()).toInstant(),
                    Instant.parse((String) json.get("expiresAt")));
            long remaining = ((Number) json.get("expiresInSeconds")).longValue();
            assertTrue(remaining >= 118 && remaining <= 120);
            assertEquals(fixture.session.getExpireTime(), response.getExpireTime());
            fixture.session.setExpireTime(LocalDateTime.now().minusSeconds(1));
            Map<?, ?> expired = new ObjectMapper().findAndRegisterModules().convertValue(
                    fixture.service.getSession("auth_qr"), Map.class);
            assertEquals(0L, ((Number) expired.get("expiresInSeconds")).longValue());
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void shouldKeepPublicProviderIdentityHidden() {
        QrFixture fixture = new QrFixture("new", null);
        fixture.session.setConnectionId("conn-1");
        TkOpenTiktokConnectionDO connection = TkOpenTiktokConnectionDO.builder()
                .openId("provider-secret").build();
        when(fixture.connectionMapper.selectByClientAndConnectionId("client_a", "conn-1")).thenReturn(connection);
        when(fixture.sessions.selectByAuthSessionIdForUpdate("auth_qr")).thenReturn(fixture.session);
        assertNull(fixture.service.getPublicSessionStatus("auth_qr").getProviderIdentity());
    }

    private static class QrFixture {
        final TkOpenTiktokAuthSessionMapper sessions = mock(TkOpenTiktokAuthSessionMapper.class);
        final TkOpenTiktokConnectionMapper connectionMapper = mock(TkOpenTiktokConnectionMapper.class);
        final TkOpenPublishPlatformAdapter adapter = mock(TkOpenPublishPlatformAdapter.class);
        final TkOpenApiCallbackService callbacks = mock(TkOpenApiCallbackService.class);
        final TkOpenApiSecretCipher cipher = mock(TkOpenApiSecretCipher.class);
        final TkOpenTiktokAuthSessionDO session = TkOpenTiktokAuthSessionDO.builder()
                .id(1L).authSessionId("auth_qr").clientId("client_a").authMode("QR_CODE")
                .clientTicket("ticket-expected").qrcodeToken("qr-token").status("WAITING")
                .expireTime(LocalDateTime.now().plusMinutes(5)).build();
        final TkOpenTiktokAuthService service;

        QrFixture(String status, String ticket) {
            TkOpenPublishPlatformRegistry registry = mock(TkOpenPublishPlatformRegistry.class);
            when(registry.getRequired("TIKTOK")).thenReturn(adapter);
            when(sessions.selectByClientAndSessionIdForUpdate("client_a", "auth_qr")).thenReturn(session);
            when(adapter.checkQrCode("qr-token")).thenReturn(
                    new TkOpenPublishPlatformAdapter.QrStatusResult(true, status, "qr-code", ticket, null));
            service = new TkOpenTiktokAuthService(sessions, connectionMapper, registry, cipher, callbacks, null,
                    "https://example.com/callback", "https://example.com/sessions");
            TkOpenApiContext.set(new TkOpenApiPrincipal("client_a", "A", "auth"), "req-qr");
        }
    }

    @Test
    void shouldAcceptAutoAuthorizationMode() throws NoSuchFieldException {
        Pattern constraint = TkOpenTiktokAuthVO.SessionCreateReq.class
                .getDeclaredField("authMode")
                .getAnnotation(Pattern.class);

        assertTrue(java.util.regex.Pattern.matches(constraint.regexp(), "AUTO"));
    }

    @Test
    void shouldRejectBlankOauthStateBeforeDatabaseLookup() {
        TkOpenTiktokAuthSessionMapper sessionMapper = mock(TkOpenTiktokAuthSessionMapper.class);
        TkOpenTiktokAuthService service = new TkOpenTiktokAuthService(sessionMapper,
                null, null, null, null, null,
                "https://tkassetplant.fnn.net.cn/admin-api/tk/open/v1/tiktok/auth/callback",
                "https://tkassetplant.fnn.net.cn/admin-api/tk/open/v1/tiktok/auth/sessions");

        TkOpenTiktokAuthCallbackResult result = service.handleCallback("code", " ", null, null);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("state"));
        verifyNoInteractions(sessionMapper);
    }

    @Test
    void shouldTreatRepeatedCallbackForSuccessfulSessionAsIdempotent() {
        TkOpenTiktokAuthSessionMapper sessionMapper = mock(TkOpenTiktokAuthSessionMapper.class);
        TkOpenTiktokConnectionMapper connectionMapper = mock(TkOpenTiktokConnectionMapper.class);
        TkOpenPublishPlatformRegistry platformRegistry = mock(TkOpenPublishPlatformRegistry.class);
        TkOpenApiSecretCipher secretCipher = mock(TkOpenApiSecretCipher.class);
        TkOpenApiCallbackService callbackService = mock(TkOpenApiCallbackService.class);
        TkOpenTiktokAuthSessionDO session = TkOpenTiktokAuthSessionDO.builder()
                .id(1L).oauthState("state-1").status("SUCCESS")
                .expireTime(LocalDateTime.now().plusMinutes(5)).build();
        when(sessionMapper.selectByOauthStateForUpdate("state-1")).thenReturn(session);
        TkOpenTiktokAuthService service = new TkOpenTiktokAuthService(sessionMapper,
                connectionMapper, platformRegistry, secretCipher, callbackService, null,
                "https://tkassetplant.fnn.net.cn/admin-api/tk/open/v1/tiktok/auth/callback",
                "https://tkassetplant.fnn.net.cn/admin-api/tk/open/v1/tiktok/auth/sessions");

        TkOpenTiktokAuthCallbackResult result = service.handleCallback("already-used-code", "state-1", null, null);

        assertTrue(result.isSuccess());
        assertEquals("SUCCESS", session.getStatus());
        verify(sessionMapper).selectByOauthStateForUpdate("state-1");
        verifyNoInteractions(connectionMapper, platformRegistry, secretCipher, callbackService);
    }

    @Test
    void shouldLockQrSessionBeforePolling() {
        TkOpenTiktokAuthSessionMapper sessionMapper = mock(TkOpenTiktokAuthSessionMapper.class);
        TkOpenTiktokAuthSessionDO session = TkOpenTiktokAuthSessionDO.builder()
                .id(1L).authSessionId("auth_1").clientId("client_a").authMode("REDIRECT")
                .status("WAITING").expireTime(LocalDateTime.now().plusMinutes(5)).build();
        when(sessionMapper.selectByClientAndSessionIdForUpdate("client_a", "auth_1")).thenReturn(session);
        TkOpenTiktokAuthService service = new TkOpenTiktokAuthService(sessionMapper,
                mock(TkOpenTiktokConnectionMapper.class), mock(TkOpenPublishPlatformRegistry.class),
                mock(TkOpenApiSecretCipher.class), mock(TkOpenApiCallbackService.class), null,
                "https://tkassetplant.fnn.net.cn/admin-api/tk/open/v1/tiktok/auth/callback",
                "https://tkassetplant.fnn.net.cn/admin-api/tk/open/v1/tiktok/auth/sessions");
        TkOpenApiContext.set(new TkOpenApiPrincipal("client_a", "A", "auth"), "req-1");

        service.getSession("auth_1");

        verify(sessionMapper).selectByClientAndSessionIdForUpdate("client_a", "auth_1");
    }

    @Test
    void shouldPrepareWebAndQrAuthorizationForAutoMode() {
        TkOpenTiktokAuthSessionMapper sessionMapper = mock(TkOpenTiktokAuthSessionMapper.class);
        TkOpenPublishPlatformRegistry platformRegistry = mock(TkOpenPublishPlatformRegistry.class);
        TkOpenPublishPlatformAdapter adapter = mock(TkOpenPublishPlatformAdapter.class);
        when(platformRegistry.getRequired("TIKTOK")).thenReturn(adapter);
        when(adapter.isConfigured()).thenReturn(true);
        when(adapter.buildAuthorizeUrl(any(), eq("https://tkassetplant.fnn.net.cn/admin-api/tk/open/v1/tiktok/auth/callback")))
                .thenReturn("https://www.tiktok.com/v2/auth/authorize/?state=state");
        when(adapter.createQrCode(any())).thenReturn(new TkOpenPublishPlatformAdapter.QrCodeResult(
                true, "qr-token", "https://www.tiktok.com/qr?client_ticket=tobefilled", null));
        TkOpenTiktokAuthService service = new TkOpenTiktokAuthService(sessionMapper,
                mock(TkOpenTiktokConnectionMapper.class), platformRegistry, mock(TkOpenApiSecretCipher.class),
                mock(TkOpenApiCallbackService.class), null,
                "https://tkassetplant.fnn.net.cn/admin-api/tk/open/v1/tiktok/auth/callback",
                "https://tkassetplant.fnn.net.cn/admin-api/tk/open/v1/tiktok/auth/sessions");
        TkOpenApiContext.set(new TkOpenApiPrincipal("client_a", "A", "auth"), "req-auto");
        TkOpenTiktokAuthVO.SessionCreateReq request = new TkOpenTiktokAuthVO.SessionCreateReq();
        request.setExternalAccountId("c-account-1");
        request.setAuthMode("AUTO");

        TkOpenTiktokAuthVO.SessionResp response = service.createSession(request);

        ArgumentCaptor<TkOpenTiktokAuthSessionDO> captor = ArgumentCaptor.forClass(TkOpenTiktokAuthSessionDO.class);
        verify(sessionMapper).insert(captor.capture());
        TkOpenTiktokAuthSessionDO session = captor.getValue();
        assertTrue(session.getClientTicket() != null && !session.getClientTicket().isEmpty());
        assertTrue(session.getQrcodeUrl().contains("client_ticket=" + session.getClientTicket()));
        assertFalse(session.getQrcodeUrl().contains("tobefilled"));
        assertEquals("https://www.tiktok.com/v2/auth/authorize/?state=state", response.getAuthorizeUrl());
        assertTrue(response.getQrcodeImageUrl().startsWith("data:image/png;base64,"));
        Map<?, ?> json = new ObjectMapper().findAndRegisterModules().convertValue(response, Map.class);
        assertTrue(json.containsKey("expiresAt"));
        assertEquals(session.getExpireTime().atZone(ZoneId.systemDefault()).toInstant(),
                Instant.parse((String) json.get("expiresAt")));
        assertTrue(((Number) json.get("expiresInSeconds")).longValue() > 890);
        assertEquals("https://tkassetplant.fnn.net.cn/admin-api/tk/open/v1/tiktok/auth/sessions/"
                + session.getAuthSessionId() + "/launch", response.getLaunchUrl());
    }

    @Test
    void shouldFailQrPollingWhenProviderTicketDoesNotMatch() {
        TkOpenTiktokAuthSessionMapper sessionMapper = mock(TkOpenTiktokAuthSessionMapper.class);
        TkOpenPublishPlatformRegistry platformRegistry = mock(TkOpenPublishPlatformRegistry.class);
        TkOpenPublishPlatformAdapter adapter = mock(TkOpenPublishPlatformAdapter.class);
        TkOpenApiCallbackService callbackService = mock(TkOpenApiCallbackService.class);
        TkOpenTiktokAuthSessionDO session = TkOpenTiktokAuthSessionDO.builder()
                .id(1L).authSessionId("auth_qr").clientId("client_a").authMode("QR_CODE")
                .clientTicket("ticket-expected").qrcodeToken("qr-token").status("WAITING")
                .expireTime(LocalDateTime.now().plusMinutes(5)).build();
        when(sessionMapper.selectByClientAndSessionIdForUpdate("client_a", "auth_qr")).thenReturn(session);
        when(platformRegistry.getRequired("TIKTOK")).thenReturn(adapter);
        when(adapter.checkQrCode("qr-token")).thenReturn(new TkOpenPublishPlatformAdapter.QrStatusResult(
                true, "waiting", null, "ticket-other", null));
        TkOpenTiktokAuthService service = new TkOpenTiktokAuthService(sessionMapper,
                mock(TkOpenTiktokConnectionMapper.class), platformRegistry, mock(TkOpenApiSecretCipher.class),
                callbackService, null,
                "https://tkassetplant.fnn.net.cn/admin-api/tk/open/v1/tiktok/auth/callback",
                "https://tkassetplant.fnn.net.cn/admin-api/tk/open/v1/tiktok/auth/sessions");
        TkOpenApiContext.set(new TkOpenApiPrincipal("client_a", "A", "auth"), "req-ticket");

        TkOpenTiktokAuthVO.SessionStatusResp response = service.getSession("auth_qr");

        assertEquals("FAILED", response.getStatus());
        assertTrue(response.getFailReason().contains("client_ticket"));
        verify(callbackService).enqueue(eq("client_a"), eq("authorization.failed"), eq("AUTH_SESSION"),
                eq("auth_qr"), any());
    }
}

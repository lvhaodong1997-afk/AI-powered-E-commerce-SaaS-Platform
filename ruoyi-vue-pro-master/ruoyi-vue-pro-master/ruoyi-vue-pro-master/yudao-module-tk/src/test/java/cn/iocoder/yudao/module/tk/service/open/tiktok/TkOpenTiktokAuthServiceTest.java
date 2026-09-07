package cn.iocoder.yudao.module.tk.service.open.tiktok;

import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenTiktokAuthSessionDO;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class TkOpenTiktokAuthServiceTest {

    @AfterEach
    void clearContext() {
        TkOpenApiContext.clear();
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
                null, null, null, null,
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
                connectionMapper, platformRegistry, secretCipher, callbackService,
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
                mock(TkOpenApiSecretCipher.class), mock(TkOpenApiCallbackService.class),
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
                mock(TkOpenApiCallbackService.class),
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
                callbackService,
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

package cn.iocoder.yudao.module.tk.service.open.tiktok;

import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenTiktokAuthSessionDO;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenTiktokAuthSessionMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenTiktokConnectionMapper;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiSecretCipher;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiContext;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiPrincipal;
import cn.iocoder.yudao.module.tk.service.open.api.TkOpenApiCallbackService;
import cn.iocoder.yudao.module.tk.service.open.platform.TkOpenPublishPlatformRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TkOpenTiktokAuthServiceTest {

    @AfterEach
    void clearContext() {
        TkOpenApiContext.clear();
    }

    @Test
    void shouldRejectBlankOauthStateBeforeDatabaseLookup() {
        TkOpenTiktokAuthSessionMapper sessionMapper = mock(TkOpenTiktokAuthSessionMapper.class);
        TkOpenTiktokAuthService service = new TkOpenTiktokAuthService(sessionMapper,
                null, null, null, null,
                "https://tkassetplant.fnn.net.cn/admin-api/tk/open/v1/tiktok/auth/callback");

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
                "https://tkassetplant.fnn.net.cn/admin-api/tk/open/v1/tiktok/auth/callback");

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
                "https://tkassetplant.fnn.net.cn/admin-api/tk/open/v1/tiktok/auth/callback");
        TkOpenApiContext.set(new TkOpenApiPrincipal("client_a", "A", "auth"), "req-1");

        service.getSession("auth_1");

        verify(sessionMapper).selectByClientAndSessionIdForUpdate("client_a", "auth_1");
    }
}

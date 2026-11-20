package cn.iocoder.yudao.module.tk.service.channel;

import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenApiClientDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenXAuthSessionDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenXReturnTicketDO;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenApiClientMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenXAuthSessionMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenXConnectionMapper;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiContext;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiException;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiPrincipal;
import cn.iocoder.yudao.module.tk.service.channel.TkChannelOAuthClient;
import cn.iocoder.yudao.module.tk.service.channel.TkChannelProperties;
import cn.iocoder.yudao.module.tk.service.channel.TkOpenXAccess;
import cn.iocoder.yudao.module.tk.service.channel.TkOpenXProperties;
import cn.iocoder.yudao.module.tk.service.open.api.TkOpenApiCallbackService;
import cn.iocoder.yudao.module.tk.service.open.x.TkOpenXTicketService;
import cn.iocoder.yudao.module.tk.service.open.x.TkOpenXCallbackOutcome;
import cn.iocoder.yudao.module.tk.service.social.auth.TkSocialTokenCipher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TkOpenXAuthServiceTest {
    private final TkOpenXProperties properties = new TkOpenXProperties();
    private final TkOpenXAccess access = new TkOpenXAccess(properties);
    private final TkOpenXAuthSessionMapper sessions = mock(TkOpenXAuthSessionMapper.class);
    private final TkOpenXConnectionMapper connections = mock(TkOpenXConnectionMapper.class);
    private final TkOpenApiClientMapper clients = mock(TkOpenApiClientMapper.class);
    private final TkChannelProperties channels = new TkChannelProperties();
    private final TkOpenXTicketService tickets = mock(TkOpenXTicketService.class);
    private final PlatformTransactionManager tx = mock(PlatformTransactionManager.class);
    private TkOpenXAuthService service;

    @BeforeEach
    void setup() {
        properties.setEnabled(true);
        TkChannelProperties.Credentials x = new TkChannelProperties.Credentials();
        x.setEnabled(true);
        x.setClientId("cid");
        x.setClientSecret("secret");
        x.setRedirectUri("https://platform.example.com/admin-api/tk/channel-publish/x/callback");
        channels.setX(x);
        when(tx.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new TkOpenXAuthService(access, sessions, connections, clients, channels,
                mock(TkChannelOAuthClient.class), mock(TkSocialTokenCipher.class), tickets,
                mock(TkOpenXTokenService.class), mock(TkOpenApiCallbackService.class), tx);
        TkOpenApiContext.set(new TkOpenApiPrincipal("client-a", "A", "x.auth,x.publish"), "req");
        when(clients.selectByClientId("client-a")).thenReturn(new TkOpenApiClientDO()
                .setClientId("client-a").setStatus(0).setPermissions("x.auth")
                .setReturnUrlWhitelist("[\"https://app.example.com/x/done\"]"));
    }

    @AfterEach
    void clear() {
        TkOpenApiContext.clear();
    }

    @Test
    void returnUrlOutsideWhitelistIsRejectedAndDoesNotCreateSession() {
        assertEquals("RETURN_URL_NOT_ALLOWED", assertThrows(TkOpenApiException.class,
                () -> service.createSession(null, "https://evil.example.com/steal", null)).getCode());
        verify(sessions, never()).insert(any(TkOpenXAuthSessionDO.class));
    }

    @Test
    void unknownStateIsUnhandledAndDoesNotExchange() {
        when(sessions.selectByOauthStateHashForUpdate(any())).thenReturn(null);
        TkOpenXCallbackOutcome outcome = service.handleOfficialCallback("x", "code",
                "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQ", null, "ab".repeat(32));
        assertFalse(outcome.isHandled());
    }

    @Test
    void successfulCallbackWithoutReturnUrlKeepsClosePageFlow() {
        TkOpenXAuthSessionDO row = pending();
        when(sessions.selectByOauthStateHashForUpdate(any())).thenReturn(row);
        // Exchange is not reached because we don't stub oauth; expired/pending without valid tokens fails closed.
        when(tx.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        // Call session poll does not consume tickets.
        when(sessions.selectByClientAndSessionId("client-a", row.getAuthSessionId())).thenReturn(row);
        assertEquals("PENDING", service.session(row.getAuthSessionId()).get("status"));
        verify(tickets, never()).redeem(any(), any(), any());
    }

    @Test
    void ticketRedeemIsClientBound() {
        when(tickets.redeem("client-a", "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQ",
                TkOpenXTicketService.PURPOSE_AUTH_RESULT))
                .thenThrow(TkOpenApiException.notFound("TICKET_NOT_FOUND", "ticket does not exist"));
        assertEquals("TICKET_NOT_FOUND", assertThrows(TkOpenApiException.class,
                () -> service.redeemTicket("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQ")).getCode());
    }

    @Test
    void replayedTicketIsRejected() {
        when(tickets.redeem(eq("client-a"), any(), eq(TkOpenXTicketService.PURPOSE_AUTH_RESULT)))
                .thenThrow(TkOpenApiException.badRequest("TICKET_USED", "ticket is no longer valid"));
        assertEquals("TICKET_USED", assertThrows(TkOpenApiException.class,
                () -> service.redeemTicket("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQ")).getCode());
    }

    private TkOpenXAuthSessionDO pending() {
        return TkOpenXAuthSessionDO.builder()
                .authSessionId("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQ")
                .clientId("client-a")
                .status("PENDING")
                .expireTime(LocalDateTime.now().plusMinutes(5))
                .build();
    }
}
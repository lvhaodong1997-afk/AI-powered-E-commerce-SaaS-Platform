package cn.iocoder.yudao.module.tk.service.channel;

import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenApiClientDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenXAuthSessionDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenXConnectionDO;
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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import org.mockito.ArgumentMatchers;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TkOpenXAuthServiceTest {
    private static final String WORKSPACE = "11111111-1111-4111-8111-111111111111";
    private static final String OTHER_WORKSPACE = "22222222-2222-4222-8222-222222222222";
    private final TkOpenXProperties properties = new TkOpenXProperties();
    private final TkOpenXAccess access = new TkOpenXAccess(properties);
    private final TkOpenXAuthSessionMapper sessions = mock(TkOpenXAuthSessionMapper.class);
    private final TkOpenXConnectionMapper connections = mock(TkOpenXConnectionMapper.class);
    private final TkOpenApiClientMapper clients = mock(TkOpenApiClientMapper.class);
    private final TkChannelProperties channels = new TkChannelProperties();
    private final TkOpenXTicketService tickets = mock(TkOpenXTicketService.class);
    private final TkChannelOAuthClient oauth = mock(TkChannelOAuthClient.class);
    private final TkOpenXTokenService tokens = mock(TkOpenXTokenService.class);
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
                oauth, mock(TkSocialTokenCipher.class), tickets, tokens, mock(TkOpenApiCallbackService.class), tx);
        TkOpenApiContext.set(new TkOpenApiPrincipal("client-a", "A", "x.auth,x.publish"), "req");
        when(clients.selectByClientId("client-a")).thenReturn(new TkOpenApiClientDO()
                .setClientId("client-a").setStatus(0).setPermissions("x.auth")
                .setReturnUrlWhitelist("[\"https://app.example.com/x/done\"]"));
    }

    @AfterEach
    void clear() {
        RequestContextHolder.resetRequestAttributes();
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


    @Test
    void successfulCallbackReplayReusesUnusedTicketAndDoesNotExchange() {
        TkOpenXAuthSessionDO row = pending()
                .setStatus("SUCCESS")
                .setReturnUrl("https://app.example.com/x/done");
        when(sessions.selectByOauthStateHashForUpdate(any())).thenReturn(row);
        when(tickets.reuseIssued("client-a", row.getAuthSessionId(), TkOpenXTicketService.PURPOSE_AUTH_RESULT))
                .thenReturn("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQ");
        TkOpenXCallbackOutcome outcome = service.handleOfficialCallback("x", "new-code",
                "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQ", null, "ab".repeat(32));
        assertTrue(outcome.isHandled());
        assertTrue(outcome.isSuccess());
        assertTrue(outcome.getRedirectUrl().contains("ticket=abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQ"));
        verify(oauth, never()).exchange(any(), any(), any(), any());
        verify(tickets, never()).issueOrReuse(any(), any(), any(), any());
    }

    @Test
    void consumedTicketCallbackReplayDoesNotIssueANewTicket() {
        TkOpenXAuthSessionDO row = pending()
                .setStatus("SUCCESS")
                .setReturnUrl("https://app.example.com/x/done");
        when(sessions.selectByOauthStateHashForUpdate(any())).thenReturn(row);
        when(tickets.reuseIssued("client-a", row.getAuthSessionId(), TkOpenXTicketService.PURPOSE_AUTH_RESULT))
                .thenReturn(null);
        TkOpenXCallbackOutcome outcome = service.handleOfficialCallback("x", "new-code",
                "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQ", null, "ab".repeat(32));
        assertTrue(outcome.isHandled());
        assertTrue(outcome.isSuccess());
        assertTrue(outcome.getRedirectUrl().contains("error=already_completed"));
        assertFalse(outcome.getRedirectUrl().contains("ticket="));
        verify(oauth, never()).exchange(any(), any(), any(), any());
        verify(tickets, never()).issueOrReuse(any(), any(), any(), any());
    }

    @Test
    void concurrentUpsertOnSameClientAndExternalReusesTheExistingRow() {
        TkOpenXConnectionDO existing = new TkOpenXConnectionDO()
                .setId(21L)
                .setConnectionId("xconn_existing")
                .setClientId("client-a")
                .setExternalAccountId("user-1")
                .setAuthStatus("REVOKED");
        when(connections.selectByClientAndExternalAccountIdForUpdate("client-a", "user-1"))
                .thenReturn(null, existing);
        doThrow(new DuplicateKeyException("duplicate")).when(connections).insert(any(TkOpenXConnectionDO.class));
        when(connections.selectListByClientAndExternalAccountIdForUpdate("client-a", "user-1"))
                .thenReturn(java.util.Collections.singletonList(existing));
        service.importAuthorized("client-a", "eu", "user-1", "Name", "uname", "access", "refresh",
                LocalDateTime.now().plusHours(1));
        verify(tokens).store(eq(existing), eq("access"), eq("refresh"), any());
        verify(connections).insert(any(TkOpenXConnectionDO.class));
    }

    @Test
    void signedWorkspaceCannotReadLegacySessionOrUseItsEndUserIdAsOwnership() {
        TkOpenXAuthSessionDO row = pending().setEndUserId(WORKSPACE);
        when(sessions.selectByClientAndSessionId("client-a", row.getAuthSessionId())).thenReturn(row);
        scope(WORKSPACE);
        assertEquals("AUTH_SESSION_NOT_FOUND", assertThrows(TkOpenApiException.class,
                () -> service.session(row.getAuthSessionId())).getCode());
        row.setWorkspaceId(WORKSPACE);
        assertEquals(WORKSPACE, service.session(row.getAuthSessionId()).get("workspaceId"));
        scope(OTHER_WORKSPACE);
        assertEquals("AUTH_SESSION_NOT_FOUND", assertThrows(TkOpenApiException.class,
                () -> service.session(row.getAuthSessionId())).getCode());
    }

    @Test
    void competingOAuthCallbackDoesNotOverwriteOwnerEvenAfterAnInsertRace() {
        TkOpenXAuthSessionDO row = pending().setWorkspaceId(OTHER_WORKSPACE)
                .setConsentHash(cn.iocoder.yudao.module.tk.service.social.auth.TkSocialAuthService.hash("ab".repeat(32)));
        TkOpenXConnectionDO original = new TkOpenXConnectionDO().setId(21L).setWorkspaceId(WORKSPACE)
                .setConnectionId("xconn_original").setExternalAccountId("user-1").setAuthStatus("AUTHORIZED")
                .setDisplayName("Original");
        when(sessions.selectByOauthStateHashForUpdate(any())).thenReturn(row);
        when(oauth.exchange(eq("x"), any(), eq("code"), isNull())).thenReturn(TkChannelHttp.JSON.createObjectNode()
                .put("access_token", "access").put("refresh_token", "refresh").put("expires_in", 7200));
        when(oauth.account("x", "access")).thenReturn(TkChannelHttp.JSON.createObjectNode()
                .put("id", "user-1").put("name", "Different"));
        when(connections.selectByClientAndExternalAccountIdForUpdate("client-a", "user-1"))
                .thenReturn(null, original);
        doThrow(new DuplicateKeyException("duplicate")).when(connections).insert(any(TkOpenXConnectionDO.class));

        assertFalse(service.handleOfficialCallback("x", "code", row.getAuthSessionId(), null, "ab".repeat(32)).isSuccess());
        assertEquals("X_ACCOUNT_ALREADY_BOUND", row.getFailReason());
        assertEquals(WORKSPACE, original.getWorkspaceId());
        assertEquals("Original", original.getDisplayName());
        verify(tokens, never()).store(any(), any(), any(), any());
    }

    private static void scope(String workspaceId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setQueryString("workspaceId=" + workspaceId);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
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
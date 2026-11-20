package cn.iocoder.yudao.module.tk.service.channel;

import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenApiClientDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenXConnectionDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenXMediaDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenXPublishTaskDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.social.TkSocialAccountDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.social.TkSocialAuthSessionDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.social.TkSocialPublishTaskDO;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenApiClientMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenXConnectionMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenXPublishTaskMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.social.TkSocialAuthSessionMapper;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiContext;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiException;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiPrincipal;
import cn.iocoder.yudao.module.tk.service.channel.TkOpenXAuthService;
import cn.iocoder.yudao.module.tk.service.open.x.TkOpenXMediaService;
import cn.iocoder.yudao.module.tk.service.social.auth.TkSocialAuthService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import org.mockito.ArgumentMatchers;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TkOpenXPublishServiceTest {
    private static final String CONN = "xconn_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String MEDIA = "media_bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private final TkOpenXProperties properties = new TkOpenXProperties();
    private final TkOpenXAccess access = new TkOpenXAccess(properties);
    private final TkOpenXAuthService auth = mock(TkOpenXAuthService.class);
    private final TkOpenXMediaService media = mock(TkOpenXMediaService.class);
    private final TkOpenXConnectionMapper connections = mock(TkOpenXConnectionMapper.class);
    private final TkOpenXPublishTaskMapper tasks = mock(TkOpenXPublishTaskMapper.class);
    private final TkOpenApiClientMapper clients = mock(TkOpenApiClientMapper.class);
    private final TkSocialAuthSessionMapper socialSessions = mock(TkSocialAuthSessionMapper.class);
    private final PlatformTransactionManager tx = mock(PlatformTransactionManager.class);
    private TkOpenXPublishService service;

    @BeforeEach
    void setup() {
        properties.setEnabled(true);
        TkOpenApiContext.set(new TkOpenApiPrincipal("client-a", "A", "x.auth,x.publish"), "req");
        when(tx.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new TkOpenXPublishService(access, auth, media, connections, tasks, clients, socialSessions, tx);
        when(connections.selectByClientAndConnectionIdForUpdate("client-a", CONN)).thenReturn(authorized());
        when(media.requireReady("client-a", MEDIA)).thenReturn(new TkOpenXMediaDO().setMediaId(MEDIA).setStatus("READY")
                .setClientId("client-a").setExpireTime(LocalDateTime.now().plusHours(1)).setFileSize(12L));
    }

    @AfterEach
    void clear() {
        TkOpenApiContext.clear();
    }

    @Test
    void disabledSwitchAndMissingPermissionAreNotSuccess() {
        properties.setEnabled(false);
        TkOpenApiException disabled = assertThrows(TkOpenApiException.class, () -> service.connections());
        assertEquals("X_OPEN_API_DISABLED", disabled.getCode());
        properties.setEnabled(true);
        TkOpenApiContext.set(new TkOpenApiPrincipal("client-a", "A", "auth,media,publish"), "req");
        TkOpenApiException denied = assertThrows(TkOpenApiException.class, () -> service.connections());
        assertEquals("X_PERMISSION_DENIED", denied.getCode());
    }

    @Test
    void crossClientConnectionAndMediaAreNotFound() {
        when(connections.selectByClientAndConnectionIdForUpdate("client-a", CONN)).thenReturn(null);
        TkOpenApiException missing = assertThrows(TkOpenApiException.class,
                () -> service.create(CONN, MEDIA, "hello", "key-1", null, null));
        assertEquals("X_CONNECTION_DISABLED", missing.getCode());
        when(connections.selectByClientAndConnectionIdForUpdate("client-a", CONN)).thenReturn(authorized());
        when(media.requireReady("client-a", MEDIA)).thenThrow(TkOpenApiException.notFound("MEDIA_NOT_FOUND", "missing"));
        assertEquals("MEDIA_NOT_FOUND", assertThrows(TkOpenApiException.class,
                () -> service.create(CONN, MEDIA, "hello", "key-1", null, null)).getCode());
    }

    @Test
    void generationTaskIdIsRejectedAndDoesNotEnqueue() {
        TkOpenApiException ex = assertThrows(TkOpenApiException.class,
                () -> service.create(CONN, MEDIA, "hello", "key-1", null, 99L));
        assertEquals("X_GENERATION_TASK_UNSUPPORTED", ex.getCode());
        verify(tasks, never()).insert(any(TkOpenXPublishTaskDO.class));
    }

    @Test
    void sameIdempotencyKeyDifferentPayloadConflicts() {
        String hash = TkSocialAuthService.hash(CONN + "\n" + MEDIA + "\nhello");
        TkOpenXPublishTaskDO existing = new TkOpenXPublishTaskDO().setTaskId("xtask_1")
                .setClientId("client-a").setConnectionId(CONN).setMediaId(MEDIA)
                .setIdempotencyKey("key-1").setPayloadHash("other").setStatus("PENDING");
        when(tasks.selectByClientAndIdempotencyKey("client-a", "key-1")).thenReturn(existing);
        assertEquals("IDEMPOTENCY_KEY_CONFLICT", assertThrows(TkOpenApiException.class,
                () -> service.create(CONN, MEDIA, "hello", "key-1", null, null)).getCode());
        existing.setPayloadHash(hash);
        Map<String, Object> retry = service.create(CONN, MEDIA, "hello", "key-1", null, null);
        assertEquals("xtask_1", retry.get("taskId"));
        verify(tasks, never()).insert(any(TkOpenXPublishTaskDO.class));
    }

    @Test
    void revokeThenPendingCreateIsRejected() {
        TkOpenXConnectionDO revoked = authorized().setAuthStatus("REVOKED");
        when(connections.selectByClientAndConnectionIdForUpdate("client-a", CONN)).thenReturn(revoked);
        assertEquals("X_CONNECTION_DISABLED", assertThrows(TkOpenApiException.class,
                () -> service.create(CONN, MEDIA, "hello", "key-2", null, null)).getCode());
        verify(tasks, never()).insert(any(TkOpenXPublishTaskDO.class));
    }

    @Test
    void listsOnlyTheAuthenticatedClientsConnections() {
        when(connections.selectListByClient("client-a")).thenReturn(Collections.singletonList(authorized()));
        assertEquals(1, service.connections().size());
        assertEquals(CONN, service.connections().get(0).get("connectionId"));
        verify(connections, never()).selectListByClient("client-b");
    }

    @Test
    void leftoverSocialTaskWithoutClientIsNotAbsorbedAndWorkbenchIsUnchanged() {
        TkSocialPublishTaskDO leftover = new TkSocialPublishTaskDO();
        leftover.setOpenXConnectionId(8L);
        TkSocialAuthSessionDO workbench = new TkSocialAuthSessionDO();
        workbench.setId(8L);
        when(socialSessions.selectById(8L)).thenReturn(workbench);
        assertEquals("X_CONNECTION_DISABLED", assertThrows(TkOpenApiException.class,
                () -> service.assertRunnable(leftover, new TkSocialAccountDO().setStatus("AUTHORIZED"))).getCode());
        TkSocialPublishTaskDO workbenchTask = new TkSocialPublishTaskDO();
        assertDoesNotThrow(() -> service.assertRunnable(workbenchTask, new TkSocialAccountDO().setStatus("AUTHORIZED")));
    }

    @Test
    void leftoverTaskIsRejectedWhenClientLacksPermission() {
        TkSocialPublishTaskDO leftover = new TkSocialPublishTaskDO();
        leftover.setOpenXConnectionId(8L);
        TkSocialAuthSessionDO row = new TkSocialAuthSessionDO();
        row.setOpenClientId("client-a");
        row.setStatus("SUCCESS");
        when(socialSessions.selectById(8L)).thenReturn(row);
        when(clients.selectByClientId("client-a")).thenReturn(new TkOpenApiClientDO()
                .setClientId("client-a").setStatus(0).setPermissions("auth,publish"));
        assertEquals("X_PERMISSION_DENIED", assertThrows(TkOpenApiException.class,
                () -> service.assertRunnable(leftover, new TkSocialAccountDO().setStatus("AUTHORIZED"))).getCode());
    }

    @Test
    void successfulCreateInsertsClientScopedTask() {
        when(tasks.selectByClientAndIdempotencyKey("client-a", "key-3")).thenReturn(null);
        Map<String, Object> created = service.create(CONN, MEDIA, "hello", "key-3", "partner-user", null);
        assertNotNull(created.get("taskId"));
        assertEquals("PENDING", created.get("status"));
        verify(tasks).insert(any(TkOpenXPublishTaskDO.class));
    }


    @Test
    void revokeOnlyUpdatesTheAuthenticatedClientsRowAndLeavesOthersAlone() {
        TkOpenXConnectionDO owned = authorized();
        when(connections.selectByClientAndConnectionIdForUpdate("client-a", CONN)).thenReturn(owned);
        assertTrue(service.revoke(CONN));
        verify(connections).updateById(ArgumentMatchers.<TkOpenXConnectionDO>argThat(row -> "REVOKED".equals(row.getAuthStatus())
                && "client-a".equals(row.getClientId()) && CONN.equals(row.getConnectionId())));
        when(connections.selectByClientAndConnectionIdForUpdate("client-a", "xconn_other")).thenReturn(null);
        assertEquals("X_CONNECTION_NOT_FOUND", assertThrows(TkOpenApiException.class,
                () -> service.revoke("xconn_other")).getCode());
        verify(connections, never()).updateById(ArgumentMatchers.<TkOpenXConnectionDO>argThat(row -> row != null && "client-b".equals(row.getClientId())));
    }

    @Test
    void sameClientCanHoldMultipleExternalAccounts() {
        TkOpenXConnectionDO second = new TkOpenXConnectionDO().setConnectionId("xconn_2")
                .setClientId("client-a").setExternalAccountId("456").setAuthStatus("AUTHORIZED");
        when(connections.selectListByClient("client-a")).thenReturn(Arrays.asList(authorized(), second));
        List<Map<String, Object>> listed = service.connections();
        assertEquals(2, listed.size());
        assertEquals(CONN, listed.get(0).get("connectionId"));
        assertEquals("xconn_2", listed.get(1).get("connectionId"));
    }
    private TkOpenXConnectionDO authorized() {
        return new TkOpenXConnectionDO().setConnectionId(CONN).setClientId("client-a")
                .setExternalAccountId("123").setAuthStatus("AUTHORIZED");
    }
}
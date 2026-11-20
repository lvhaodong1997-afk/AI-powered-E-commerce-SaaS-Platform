package cn.iocoder.yudao.module.tk.service.channel;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.tk.dal.dataobject.social.*;
import cn.iocoder.yudao.module.tk.dal.mysql.TkGenerationTaskMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenApiClientMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.social.*;
import cn.iocoder.yudao.module.tk.framework.openapi.*;
import cn.iocoder.yudao.module.tk.service.social.TkSocialMediaService;
import cn.iocoder.yudao.module.tk.service.social.auth.TkSocialAuthService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TkOpenXPublishServiceTest {
    private static final String ID = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    private static final String TASK = "xtask_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private final TkSocialAuthSessionMapper sessions = mock(TkSocialAuthSessionMapper.class);
    private final TkSocialAccountMapper accounts = mock(TkSocialAccountMapper.class);
    private final TkSocialPublishTaskMapper tasks = mock(TkSocialPublishTaskMapper.class);
    private final TkSocialPublishDetailMapper details = mock(TkSocialPublishDetailMapper.class);
    private final TkGenerationTaskMapper generations = mock(TkGenerationTaskMapper.class);
    private final TkChannelAccountService channels = mock(TkChannelAccountService.class);
    private final PlatformTransactionManager tx = mock(PlatformTransactionManager.class);
    private final TkOpenXProperties properties = new TkOpenXProperties();
    private final TkOpenXAccess access = new TkOpenXAccess(properties, mock(TkOpenApiClientMapper.class));
    private final TkOpenXPublishService service = new TkOpenXPublishService(access, channels, new TkChannelProperties(),
            sessions, accounts, tasks, details, generations, mock(TkSocialMediaService.class), tx);

    @BeforeEach
    void setup() {
        TkOpenApiContext.set(new TkOpenApiPrincipal("client-a", "A", "x.auth,x.publish"), "req");
        TkOpenXProperties.Grant grant = new TkOpenXProperties.Grant();
        grant.setClientId("client-a"); grant.setTenantId(7L); grant.setCompanyId(8L);
        grant.setOwnerUserId(9L); grant.setXUserId("1234");
        properties.setEnabled(true); properties.setGrants(Collections.singletonList(grant));
        when(channels.ready("x")).thenReturn(true);
        when(tx.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    }

    @AfterEach
    void clear() {
        TkOpenApiContext.clear();
        TenantContextHolder.clear();
    }

    @Test
    void crossClientResourceReturnsNotFoundAndRestoresTenant() {
        TenantContextHolder.setTenantId(99L);
        TkSocialAuthSessionDO foreign = row("client-b");
        when(sessions.selectOne(any())).thenReturn(foreign);
        assertEquals(404, assertThrows(TkOpenApiException.class, () -> service.session(ID)).getHttpStatus());
        assertEquals(99L, TenantContextHolder.getTenantId());
    }

    @Test
    void retryReturnsOriginalTaskAndChangedPayloadConflicts() {
        TkSocialPublishTaskDO task = new TkSocialPublishTaskDO();
        task.setId(21L); task.setTenantId(7L); task.setCompanyId(8L); task.setOpenXConnectionId(20L); task.setOpenXTaskId(TASK);
        task.setRequestHash(TkSocialAuthService.hash(ID + "\n42\ncaption"));
        when(tasks.selectOne(any())).thenReturn(task);
        when(sessions.selectById(20L)).thenReturn(row("client-a"));
        TkSocialPublishDetailDO detail = new TkSocialPublishDetailDO(); detail.setStatus("SUCCESS"); detail.setExternalPostId("17");
        when(details.selectOne(any())).thenReturn(detail);
        assertEquals(TASK, service.create(ID, 42L, "caption", "key").get("taskId"));
        assertEquals("SUCCEEDED", service.create(ID, 42L, "caption", "key").get("status"));
        assertEquals(409, assertThrows(TkOpenApiException.class, () -> service.create(ID, 42L, "changed", "key")).getHttpStatus());
        verify(tasks, never()).insert(any(TkSocialPublishTaskDO.class));
        verifyNoInteractions(generations);
    }

    @Test
    void revokedConnectionDoesNotReadVideoOrEnqueue() {
        TkSocialAuthSessionDO connection = row("client-a"); connection.setStatus("REVOKED");
        when(sessions.selectOne(any())).thenReturn(connection);
        assertEquals("X_CONNECTION_DISABLED", assertThrows(TkOpenApiException.class,
                () -> service.create(ID, 42L, "", "key")).getCode());
        verify(tasks, never()).insert(any(TkSocialPublishTaskDO.class));
        verifyNoInteractions(generations, details);
    }

    @Test
    void consentDoesNotStartOAuthWithoutMatchingBrowserProof() {
        assertEquals(404, assertThrows(TkOpenApiException.class,
                () -> service.consent(ID, "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "different")).getHttpStatus());
        verifyNoInteractions(sessions, channels);
    }

    private static TkSocialAuthSessionDO row(String client) {
        TkSocialAuthSessionDO row = new TkSocialAuthSessionDO();
        row.setSessionId(ID); row.setPlatform("x"); row.setOpenClientId(client); row.setStatus("SUCCESS");
        row.setTenantId(7L); row.setCompanyId(8L); row.setCreator("9"); row.setOpenAccountId(10L);
        return row;
    }
}

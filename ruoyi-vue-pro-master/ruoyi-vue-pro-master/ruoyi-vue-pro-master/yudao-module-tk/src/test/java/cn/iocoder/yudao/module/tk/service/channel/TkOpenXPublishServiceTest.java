package cn.iocoder.yudao.module.tk.service.channel;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.dal.mysql.user.AdminUserMapper;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationTaskDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenXEndUserDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.social.*;
import cn.iocoder.yudao.module.tk.dal.mysql.TkGenerationTaskMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenApiClientMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenXEndUserMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.social.*;
import cn.iocoder.yudao.module.tk.framework.openapi.*;
import cn.iocoder.yudao.module.tk.service.social.TkSocialMediaService;
import cn.iocoder.yudao.module.tk.service.social.auth.TkSocialAuthService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class TkOpenXPublishServiceTest {
    private static final String ID = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    private static final String OTHER = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB";
    private static final String TASK = "xtask_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private final TkSocialAuthSessionMapper sessions = mock(TkSocialAuthSessionMapper.class);
    private final TkSocialAccountMapper accounts = mock(TkSocialAccountMapper.class);
    private final TkSocialPublishTaskMapper tasks = mock(TkSocialPublishTaskMapper.class);
    private final TkSocialPublishDetailMapper details = mock(TkSocialPublishDetailMapper.class);
    private final TkGenerationTaskMapper generations = mock(TkGenerationTaskMapper.class);
    private final TkChannelAccountService channels = mock(TkChannelAccountService.class);
    private final PlatformTransactionManager tx = mock(PlatformTransactionManager.class);
    private final TkOpenXEndUserMapper endUsers = mock(TkOpenXEndUserMapper.class);
    private final AdminUserMapper adminUsers = mock(AdminUserMapper.class);
    private final TkOpenXProperties properties = new TkOpenXProperties();
    private final TkOpenXAccess access = new TkOpenXAccess(properties, mock(TkOpenApiClientMapper.class), endUsers, adminUsers);
    private final TkOpenXPublishService service = new TkOpenXPublishService(access, channels, new TkChannelProperties(),
            sessions, accounts, tasks, details, generations, mock(TkSocialMediaService.class), tx);

    @BeforeEach
    void setup() {
        TkOpenApiContext.set(new TkOpenApiPrincipal("client-a", "A", "x.auth,x.publish"), "req");
        usePilotGrant();
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
        when(sessions.selectOne(any())).thenReturn(row("client-b", null, "9"));
        assertEquals(404, assertThrows(TkOpenApiException.class, () -> service.session(ID, null)).getHttpStatus());
        assertEquals(99L, TenantContextHolder.getTenantId());
    }

    @Test
    void retryReturnsOriginalTaskAndChangedPayloadConflicts() {
        TkSocialPublishTaskDO task = new TkSocialPublishTaskDO();
        task.setId(21L); task.setTenantId(7L); task.setCompanyId(8L); task.setOpenXConnectionId(20L); task.setOpenXTaskId(TASK);
        task.setRequestHash(TkSocialAuthService.hash(ID + "\n42\ncaption"));
        when(tasks.selectOne(any())).thenReturn(task);
        when(sessions.selectById(20L)).thenReturn(row("client-a", null, "9"));
        TkSocialPublishDetailDO detail = new TkSocialPublishDetailDO(); detail.setStatus("SUCCESS"); detail.setExternalPostId("17");
        when(details.selectOne(any())).thenReturn(detail);
        assertEquals(TASK, service.create(ID, 42L, "caption", "key", null).get("taskId"));
        assertEquals("SUCCEEDED", service.create(ID, 42L, "caption", "key", null).get("status"));
        assertEquals(409, assertThrows(TkOpenApiException.class, () -> service.create(ID, 42L, "changed", "key", null)).getHttpStatus());
        verify(tasks, never()).insert(any(TkSocialPublishTaskDO.class));
        verifyNoInteractions(generations);
    }

    @Test
    void revokedConnectionDoesNotReadVideoOrEnqueue() {
        TkSocialAuthSessionDO connection = row("client-a", null, "9"); connection.setStatus("REVOKED");
        when(sessions.selectOne(any())).thenReturn(connection);
        assertEquals("X_CONNECTION_DISABLED", assertThrows(TkOpenApiException.class,
                () -> service.create(ID, 42L, "", "key", null)).getCode());
        verify(tasks, never()).insert(any(TkSocialPublishTaskDO.class));
        verifyNoInteractions(generations, details);
    }

    @Test
    void consentDoesNotStartOAuthWithoutMatchingBrowserProof() {
        assertEquals(404, assertThrows(TkOpenApiException.class,
                () -> service.consent(ID, "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "different")).getHttpStatus());
        verifyNoInteractions(sessions, channels);
    }

    @Test
    void multiUserRequiresEndUserId() {
        useMultiUserGrant();
        assertEquals("X_END_USER_REQUIRED", assertThrows(TkOpenApiException.class, () -> service.session(ID, null)).getCode());
        assertEquals("X_OWNER_REQUIRED", assertThrows(TkOpenApiException.class,
                () -> service.createSession("user-a", null)).getCode());
    }

    @Test
    void sameClientUsersCannotUseEachOthersConnectionOrVideo() {
        useMultiUserGrant();
        bind("user-a", 11L);
        bind("user-b", 12L);
        TkSocialAuthSessionDO userA = row("client-a", "user-a", "11");
        when(sessions.selectOne(any())).thenReturn(userA);
        assertEquals(404, assertThrows(TkOpenApiException.class, () -> service.session(ID, "user-b")).getHttpStatus());
        assertEquals(404, assertThrows(TkOpenApiException.class, () -> service.revoke(ID, "user-b")).getHttpStatus());

        TkGenerationTaskDO foreignVideo = new TkGenerationTaskDO();
        foreignVideo.setId(42L); foreignVideo.setTenantId(7L); foreignVideo.setCompanyId(8L);
        foreignVideo.setCreator("11"); foreignVideo.setStatus("SUCCESS"); foreignVideo.setOutputUrl("https://example/v.mp4");
        when(sessions.selectOne(any())).thenReturn(row("client-a", "user-b", "12"));
        when(accounts.selectById(10L)).thenReturn(account("555", "12"));
        when(generations.selectOne(any())).thenReturn(null);
        assertEquals("X_VIDEO_NOT_AVAILABLE", assertThrows(TkOpenApiException.class,
                () -> service.create(ID, 42L, "", "key-b", "user-b")).getCode());
        verify(tasks, never()).insert(any(TkSocialPublishTaskDO.class));
    }

    @Test
    void crossClientEndUserIsRejected() {
        useMultiUserGrant();
        bind("user-a", 11L);
        TkOpenApiContext.set(new TkOpenApiPrincipal("client-b", "B", "x.auth,x.publish"), "req");
        TkOpenXProperties.Grant other = scope("client-b");
        properties.setGrants(Arrays.asList(properties.getGrants().get(0), other));
        assertEquals("X_END_USER_NOT_BOUND", assertThrows(TkOpenApiException.class,
                () -> service.session(ID, "user-a")).getCode());
    }

    @Test
    void ownerOutsideTenantOrCompanyIsRejected() {
        useMultiUserGrant();
        AdminUserDO user = new AdminUserDO();
        user.setId(99L); user.setTenantId(7L); user.setTkCompanyId(80L); user.setStatus(0);
        when(adminUsers.selectById(99L)).thenReturn(user);
        assertEquals("X_OWNER_INVALID", assertThrows(TkOpenApiException.class,
                () -> service.createSession("user-a", 99L)).getCode());
        verify(endUsers, never()).insert(any(TkOpenXEndUserDO.class));
    }

    @Test
    void endUserCannotRebindToAnotherOwner() {
        useMultiUserGrant();
        bind("user-a", 11L);
        assertEquals("X_END_USER_OWNER_CONFLICT", assertThrows(TkOpenApiException.class,
                () -> service.createSession("user-a", 12L)).getCode());
    }

    @Test
    void multiUserIdempotentRetryStaysOnSameEndUser() {
        useMultiUserGrant();
        bind("user-a", 11L);
        TkSocialPublishTaskDO task = new TkSocialPublishTaskDO();
        task.setId(21L); task.setTenantId(7L); task.setCompanyId(8L); task.setOpenXConnectionId(20L);
        task.setOpenXTaskId(TASK); task.setOpenEndUserId("user-a");
        task.setRequestHash(TkSocialAuthService.hash(ID + "\n42\ncaption"));
        when(tasks.selectOne(any())).thenReturn(task);
        when(sessions.selectById(20L)).thenReturn(row("client-a", "user-a", "11"));
        TkSocialPublishDetailDO detail = new TkSocialPublishDetailDO(); detail.setStatus("PENDING");
        when(details.selectOne(any())).thenReturn(detail);
        assertEquals(TASK, service.create(ID, 42L, "caption", "key", "user-a").get("taskId"));
        assertEquals(409, assertThrows(TkOpenApiException.class,
                () -> service.create(ID, 42L, "changed", "key", "user-a")).getHttpStatus());
        verify(tasks, never()).insert(any(TkSocialPublishTaskDO.class));
    }

    @Test
    void listsOnlyTheAuthenticatedEndUsersConnections() {
        useMultiUserGrant();
        bind("user-a", 11L);
        TkSocialAuthSessionDO mine = row("client-a", "user-a", "11");
        mine.setSessionId(ID);
        when(sessions.selectList(ArgumentMatchers.<QueryWrapper<TkSocialAuthSessionDO>>any()))
                .thenReturn(new ArrayList<>(Collections.singletonList(mine)));
        when(accounts.selectById(10L)).thenReturn(account("555", "11"));
        List<?> listed = service.connections("user-a");
        assertEquals(1, listed.size());
    }

    private void usePilotGrant() {
        TkOpenXProperties.Grant grant = new TkOpenXProperties.Grant();
        grant.setClientId("client-a"); grant.setTenantId(7L); grant.setCompanyId(8L);
        grant.setOwnerUserId(9L); grant.setXUserId("1234");
        properties.setEnabled(true); properties.setGrants(Collections.singletonList(grant));
    }

    private void useMultiUserGrant() {
        properties.setEnabled(true);
        properties.setGrants(Collections.singletonList(scope("client-a")));
    }

    private static TkOpenXProperties.Grant scope(String clientId) {
        TkOpenXProperties.Grant grant = new TkOpenXProperties.Grant();
        grant.setClientId(clientId); grant.setTenantId(7L); grant.setCompanyId(8L);
        return grant;
    }

    private void bind(String endUserId, Long ownerUserId) {
        TkOpenXEndUserDO row = new TkOpenXEndUserDO();
        row.setClientId("client-a"); row.setEndUserId(endUserId);
        row.setTenantId(7L); row.setCompanyId(8L); row.setOwnerUserId(ownerUserId);
        when(endUsers.findByClientAndEndUser("client-a", endUserId)).thenReturn(row);
        AdminUserDO user = new AdminUserDO();
        user.setId(ownerUserId); user.setTenantId(7L); user.setTkCompanyId(8L); user.setStatus(0);
        when(adminUsers.selectById(ownerUserId)).thenReturn(user);
    }

    private static TkSocialAuthSessionDO row(String client, String endUserId, String creator) {
        TkSocialAuthSessionDO row = new TkSocialAuthSessionDO();
        row.setId(20L);
        row.setSessionId(ID); row.setPlatform("x"); row.setOpenClientId(client); row.setStatus("SUCCESS");
        row.setTenantId(7L); row.setCompanyId(8L); row.setCreator(creator); row.setOpenAccountId(10L);
        row.setOpenEndUserId(endUserId);
        return row;
    }

    private static TkSocialAccountDO account(String xUserId, String creator) {
        TkSocialAccountDO account = new TkSocialAccountDO();
        account.setId(10L); account.setPlatform("x"); account.setStatus("AUTHORIZED");
        account.setTenantId(7L); account.setCompanyId(8L); account.setCreator(creator);
        account.setExternalAccountId(xUserId); account.setAccountName("Name");
        return account;
    }
}

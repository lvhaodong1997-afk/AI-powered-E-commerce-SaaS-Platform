package cn.iocoder.yudao.module.system.service.tenant;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.dal.dataobject.tenant.*;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.dal.mysql.tenant.*;
import cn.iocoder.yudao.module.system.dal.mysql.user.AdminUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDateTime;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class TenantInviteServiceTest {
    TenantInviteService service = new TenantInviteService();
    TenantInviteMapper invites = mock(TenantInviteMapper.class);
    TenantReferralMapper referrals = mock(TenantReferralMapper.class);
    TenantService tenants = mock(TenantService.class);
    AdminUserMapper users = mock(AdminUserMapper.class);
    @BeforeEach void setup() {
        ReflectionTestUtils.setField(service,"inviteMapper",invites); ReflectionTestUtils.setField(service,"referralMapper",referrals);
        ReflectionTestUtils.setField(service,"tenantService",tenants); ReflectionTestUtils.setField(service,"userMapper",users);
        ReflectionTestUtils.setField(service,"properties",new TenantRegistrationProperties());
        when(tenants.getTenant(5L)).thenReturn(new TenantDO().setId(5L).setPackageId(9L).setStatus(0).setExpireTime(LocalDateTime.now().plusDays(1)));
    }
    @Test void emptyInviteIsOptional() { assertNull(service.validateInvite(null)); assertNull(service.validateInvite("")); verifyNoInteractions(invites); }
    @Test void invalidInviteRejected() { assertThrows(RuntimeException.class, () -> service.validateInvite("ABCDEFGHIJ")); }
    @Test void platformInviteCannotRefer() {
        when(invites.selectByCode("ABCDEFGHIJ")).thenReturn(new TenantInviteDO().setTenantId(5L));
        when(tenants.getTenant(5L)).thenReturn(new TenantDO().setId(5L).setPackageId(0L).setStatus(0));
        assertThrows(RuntimeException.class, () -> service.validateInvite("ABCDEFGHIJ"));
    }
    @Test void ordinaryMemberCannotReadInvite() {
        try (MockedStatic<SecurityFrameworkUtils> security=mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            AdminUserDO member = new AdminUserDO().setStatus(0).setTkUserLevel("TENANT_USER"); member.setTenantId(5L);
            when(users.selectById(7L)).thenReturn(member);
            assertThrows(RuntimeException.class, service::my); verifyNoInteractions(invites,referrals);
        }
    }
    @Test void identityComesFromDatabaseNotTenantHeader() {
        try (MockedStatic<SecurityFrameworkUtils> security=mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            AdminUserDO admin = new AdminUserDO().setStatus(0).setTkUserLevel("TENANT_ADMIN"); admin.setTenantId(5L);
            when(users.selectById(7L)).thenReturn(admin);
            when(invites.selectByTenantId(5L)).thenReturn(new TenantInviteDO().setTenantId(5L).setInviteCode("ABCDEFGHIJ"));
            when(referrals.countByInviter(5L)).thenReturn(2L);
            TenantContextHolder.setTenantId(999L);
            try { assertEquals("ABCDEFGHIJ", service.my().getInviteCode()); verify(invites,never()).selectByTenantId(999L); }
            finally { TenantContextHolder.clear(); }
        }
    }
    @Test void ownCodeIsOpaqueAndExactlyTenCharacters() {
        String code = service.createCode(5L); assertTrue(code.matches("[A-Z0-9]{10}"));
        verify(invites).insert(argThat((TenantInviteDO v) -> v.getTenantId()==5L && v.getInviteCode().equals(code)));
    }
    @Test void namesAreMasked() { assertEquals("A***",TenantInviteService.maskName("Acme")); assertEquals("***",TenantInviteService.maskName("A")); }
}

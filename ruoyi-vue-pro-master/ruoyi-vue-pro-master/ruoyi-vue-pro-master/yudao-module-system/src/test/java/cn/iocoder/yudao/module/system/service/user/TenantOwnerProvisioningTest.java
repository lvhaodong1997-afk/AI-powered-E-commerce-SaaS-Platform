package cn.iocoder.yudao.module.system.service.user;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.controller.admin.user.vo.user.UserSaveReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.dal.mysql.user.AdminUserMapper;
import cn.iocoder.yudao.module.system.service.tenant.TenantService;
import cn.iocoder.yudao.module.system.service.tenant.TkTenantAccessService;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.concurrent.Callable;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TenantOwnerProvisioningTest {
    @Test
    void anonymousTrustedProvisioningCreatesOwnerInExplicitTenant() {
        AdminUserServiceImpl service = new AdminUserServiceImpl();
        AdminUserMapper mapper = mock(AdminUserMapper.class);
        TkTenantAccessService access = mock(TkTenantAccessService.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        ReflectionTestUtils.setField(service, "userMapper", mapper);
        ReflectionTestUtils.setField(service, "tenantService", mock(TenantService.class));
        ReflectionTestUtils.setField(service, "tkTenantAccessService", access);
        ReflectionTestUtils.setField(service, "passwordEncoder", encoder);
        when(access.executeCurrentTenantForNonPlatform(any(Callable.class))).thenAnswer(i -> ((Callable<?>) i.getArgument(0)).call());
        when(encoder.encode(anyString())).thenReturn("encoded");
        doAnswer(i -> { AdminUserDO u = i.getArgument(0); u.setId(10L); return 1; }).when(mapper).insert(any(AdminUserDO.class));
        TenantContextHolder.setTenantId(123L);
        try {
            assertEquals(10L, service.createTenantAdminUser(new UserSaveReqVO().setUsername("owner123").setPassword("Password1").setNickname("Owner").setTenantId(123L).setTkUserLevel("TENANT_ADMIN")));
            verify(mapper).insert(argThat((AdminUserDO u) -> "TENANT_ADMIN".equals(u.getTkUserLevel()) && Long.valueOf(123).equals(u.getTenantId())));
        } finally { TenantContextHolder.clear(); }
    }

    @Test
    void genericCreationStillRejectsOwnerRole() {
        assertThrows(RuntimeException.class, () -> new AdminUserServiceImpl().createUser(new UserSaveReqVO().setTkUserLevel("TENANT_ADMIN")));
    }

    @Test
    void newRegistrationCannotBypassOtpThroughLegacyUserRegistration() {
        AdminUserServiceImpl service = new AdminUserServiceImpl();
        ReflectionTestUtils.setField(service, "tenantRegistrationEnabled", true);
        assertThrows(RuntimeException.class, () -> service.registerUser(new cn.iocoder.yudao.module.system.controller.admin.auth.vo.AuthRegisterReqVO()));
    }

    @Test
    void provisioningRequiresExplicitMatchingPositiveTenant() {
        TenantContextHolder.setTenantId(123L);
        try {
            assertThrows(RuntimeException.class, () -> new AdminUserServiceImpl().createTenantAdminUser(new UserSaveReqVO().setTenantId(456L)));
        } finally { TenantContextHolder.clear(); }
    }
}

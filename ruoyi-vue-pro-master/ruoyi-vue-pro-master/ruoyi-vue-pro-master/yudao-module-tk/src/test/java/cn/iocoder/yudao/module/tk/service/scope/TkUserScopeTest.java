package cn.iocoder.yudao.module.tk.service.scope;

import cn.iocoder.yudao.module.tk.enums.TkUserLevelEnum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TkUserScopeTest {

    @Test
    void tenantUserCannotReadAllTenantRecords() {
        TkUserScope scope = new TkUserScope(7L, 100L, TkUserLevelEnum.TENANT_USER.getCode(), 200L);

        assertFalse(scope.canReadAllTenantRecords());
    }

    @Test
    void companyUserCannotReadAllTenantRecords() {
        TkUserScope scope = new TkUserScope(7L, 100L, TkUserLevelEnum.COMPANY_USER.getCode(), 200L);

        assertFalse(scope.canReadAllTenantRecords());
    }

    @Test
    void tenantAdminCanReadAllTenantRecords() {
        TkUserScope scope = new TkUserScope(7L, 100L, TkUserLevelEnum.TENANT_ADMIN.getCode(), null);

        assertTrue(scope.canReadAllTenantRecords());
    }

    @Test
    void companyAdminCanReadAllTenantRecords() {
        TkUserScope scope = new TkUserScope(7L, 100L, TkUserLevelEnum.COMPANY_ADMIN.getCode(), 200L);

        assertTrue(scope.canReadAllTenantRecords());
    }

    @Test
    void platformAdminCanReadAllTenantRecords() {
        TkUserScope scope = new TkUserScope(7L, null, TkUserLevelEnum.PLATFORM_ADMIN.getCode(), null);

        assertTrue(scope.canReadAllTenantRecords());
    }

    @Test
    void unknownUserLevelCannotReadTenantRecords() {
        TkUserScope scope = new TkUserScope(7L, 100L, "UNKNOWN", null);

        assertFalse(scope.canReadAllTenantRecords());
    }

}

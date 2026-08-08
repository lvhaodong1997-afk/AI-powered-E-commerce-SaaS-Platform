package cn.iocoder.yudao.module.tk.service.scope;

import cn.iocoder.yudao.module.tk.enums.TkUserLevelEnum;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TkUserScope {

    private Long userId;
    private Long tenantId;
    private String userLevel;
    private Long companyId;

    public boolean isPlatformAdmin() {
        return TkUserLevelEnum.isPlatformAdmin(userLevel);
    }

    public boolean isTenantAdmin() {
        return TkUserLevelEnum.isTenantAdmin(userLevel);
    }

    public boolean canReadAllTenantRecords() {
        return isPlatformAdmin() || isTenantAdmin();
    }

    public boolean hasTenantScope() {
        return tenantId != null && tenantId > 0;
    }

    public boolean isGlobalPlatformView() {
        return isPlatformAdmin() && !hasTenantScope();
    }

    public String getUserIdString() {
        return userId == null ? null : String.valueOf(userId);
    }

}

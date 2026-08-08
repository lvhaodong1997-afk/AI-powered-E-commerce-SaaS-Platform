package cn.iocoder.yudao.module.tk.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum TkUserLevelEnum {

    PLATFORM_ADMIN("PLATFORM_ADMIN", "一级用户 / 平台管理员"),
    TENANT_ADMIN("TENANT_ADMIN", "租户管理员"),
    TENANT_USER("TENANT_USER", "三级普通用户"),
    COMPANY_ADMIN("COMPANY_ADMIN", "租户管理员"),
    COMPANY_USER("COMPANY_USER", "三级普通用户");

    private final String code;
    private final String name;

    public static boolean isPlatformAdmin(String code) {
        return PLATFORM_ADMIN.code.equals(code);
    }

    public static boolean isTenantAdmin(String code) {
        return TENANT_ADMIN.code.equals(code) || COMPANY_ADMIN.code.equals(code);
    }

    public static boolean isTenantUser(String code) {
        return TENANT_USER.code.equals(code) || COMPANY_USER.code.equals(code);
    }

    public static boolean isValid(String code) {
        for (TkUserLevelEnum level : values()) {
            if (level.code.equals(code)) {
                return true;
            }
        }
        return false;
    }

}

package cn.iocoder.yudao.module.system.util;

import cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.system.dal.dataobject.permission.MenuDO;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception0;

public final class TkPlatformAdminUtils {

    private static final String INFO_KEY_TK_USER_LEVEL = "tkUserLevel";
    private static final String TK_PLATFORM_ADMIN = "PLATFORM_ADMIN";

    private TkPlatformAdminUtils() {
    }

    public static boolean isPlatformAdmin() {
        LoginUser loginUser = SecurityFrameworkUtils.getLoginUser();
        return loginUser != null && loginUser.getInfo() != null
                && TK_PLATFORM_ADMIN.equals(loginUser.getInfo().get(INFO_KEY_TK_USER_LEVEL));
    }

    public static void validatePlatformAdmin() {
        if (!isPlatformAdmin()) {
            throw exception0(GlobalErrorCodeConstants.FORBIDDEN.getCode(), "只有超级管理员可以管理平台租户");
        }
    }

    public static void removePlatformMenusForTenantUser(List<MenuDO> menuList) {
        if (menuList == null) {
            return;
        }
        menuList.removeIf(TkPlatformAdminUtils::isDisabledTkMenu);
        if (isPlatformAdmin()) {
            return;
        }
        menuList.removeIf(TkPlatformAdminUtils::isPlatformMenu);
    }

    public static Set<Long> filterPlatformMenuIdsForTenantUser(Collection<MenuDO> menuList, Collection<Long> menuIds) {
        if (menuList == null || menuIds == null) {
            return menuIds == null ? java.util.Collections.emptySet() : new HashSet<>(menuIds);
        }
        boolean platformAdmin = isPlatformAdmin();
        Set<Long> hiddenMenuIds = menuList.stream()
                .filter(menu -> isDisabledTkMenu(menu) || (!platformAdmin && isPlatformMenu(menu)))
                .map(MenuDO::getId)
                .collect(Collectors.toSet());
        return menuIds.stream()
                .filter(menuId -> !hiddenMenuIds.contains(menuId))
                .collect(Collectors.toSet());
    }

    private static boolean isPlatformMenu(MenuDO menu) {
        String permission = menu.getPermission();
        String path = menu.getPath();
        String component = menu.getComponent();
        return startsWith(permission, "system:tenant:")
                || startsWith(permission, "system:tenant-package:")
                || startsWith(permission, "system:menu:")
                || startsWith(permission, "system:dept:")
                || startsWith(permission, "system:post:")
                || startsWith(permission, "tk:company:")
                || contains(path, "tenant")
                || contains(path, "menu")
                || contains(path, "dept")
                || contains(path, "post")
                || contains(path, "company")
                || contains(component, "system/tenant")
                || contains(component, "system/menu")
                || contains(component, "system/dept")
                || contains(component, "system/post")
                || contains(component, "tk/company");
    }

    private static boolean isDisabledTkMenu(MenuDO menu) {
        String permission = menu.getPermission();
        String path = menu.getPath();
        String component = menu.getComponent();
        return startsWith(permission, "system:dept:")
                || startsWith(permission, "system:post:")
                || contains(path, "dept")
                || contains(path, "post")
                || contains(component, "system/dept")
                || contains(component, "system/post");
    }

    private static boolean startsWith(String value, String prefix) {
        return value != null && value.startsWith(prefix);
    }

    private static boolean contains(String value, String pattern) {
        return value != null && value.contains(pattern);
    }

}

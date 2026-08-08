package cn.iocoder.yudao.module.system.service.tenant;

import cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.system.dal.dataobject.permission.RoleDO;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.dal.mysql.user.AdminUserMapper;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Objects;
import java.util.concurrent.Callable;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception0;

@Service
public class TkTenantAccessService {

    private static final String TK_PLATFORM_ADMIN = "PLATFORM_ADMIN";

    @Resource
    private AdminUserMapper userMapper;

    public boolean isCurrentUserPlatformAdmin() {
        AdminUserDO user = getCurrentLoginUser();
        return user != null && TK_PLATFORM_ADMIN.equals(user.getTkUserLevel());
    }

    public Long getCurrentUserTenantId() {
        AdminUserDO user = getCurrentLoginUser();
        return user != null ? user.getTenantId() : null;
    }

    public <T> T executeCurrentTenantForNonPlatform(Callable<T> callable) {
        if (isCurrentUserPlatformAdmin()) {
            return call(callable);
        }
        Long tenantId = getCurrentUserTenantId();
        if (tenantId == null) {
            return call(callable);
        }
        return TenantUtils.execute(tenantId, callable);
    }

    public void executeCurrentTenantForNonPlatform(Runnable runnable) {
        executeCurrentTenantForNonPlatform(() -> {
            runnable.run();
            return null;
        });
    }

    public void validateUserTenant(AdminUserDO user) {
        if (user == null || isCurrentUserPlatformAdmin()) {
            return;
        }
        validateTenantId(user.getTenantId());
    }

    public void validateRoleTenant(RoleDO role) {
        if (role == null || isCurrentUserPlatformAdmin()) {
            return;
        }
        validateTenantId(role.getTenantId());
    }

    public void validateTenantId(Long tenantId) {
        Long currentTenantId = getCurrentUserTenantId();
        if (currentTenantId != null && !Objects.equals(currentTenantId, tenantId)) {
            throw exception0(GlobalErrorCodeConstants.FORBIDDEN.getCode(), "您无权访问该租户的数据");
        }
    }

    public <T extends TenantBaseDO> T applyCurrentTenantForNonPlatform(T entity) {
        if (entity == null) {
            return entity;
        }
        if (isCurrentUserPlatformAdmin()) {
            return entity;
        }
        Long tenantId = getCurrentUserTenantId();
        if (tenantId != null) {
            entity.setTenantId(tenantId);
        }
        return entity;
    }

    private AdminUserDO getCurrentLoginUser() {
        LoginUser loginUser = SecurityFrameworkUtils.getLoginUser();
        if (loginUser == null || loginUser.getId() == null) {
            return null;
        }
        return TenantUtils.executeIgnore(() -> userMapper.selectById(loginUser.getId()));
    }

    private <T> T call(Callable<T> callable) {
        try {
            return callable.call();
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

}

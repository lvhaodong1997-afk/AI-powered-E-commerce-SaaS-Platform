package cn.iocoder.yudao.module.tk.service.scope;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.web.core.util.WebFrameworkUtils;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkUserScopeDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkUserScopeMapper;
import cn.iocoder.yudao.module.tk.enums.TkUserLevelEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.tk.enums.ErrorCodeConstants.*;

@Service
@Validated
@Slf4j
public class TkDataScopeServiceImpl implements TkDataScopeService {

    @Resource
    private TkUserScopeMapper userScopeMapper;

    @Override
    public TkUserScope getCurrentScope() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (userId == null) {
            throw exception(TK_USER_SCOPE_NOT_CONFIGURED);
        }
        TkUserScopeDO user = userScopeMapper.selectById(userId);
        if (user == null || user.getTkUserLevel() == null || !TkUserLevelEnum.isValid(user.getTkUserLevel())) {
            throw exception(TK_USER_SCOPE_NOT_CONFIGURED);
        }
        boolean platformAdmin = TkUserLevelEnum.isPlatformAdmin(user.getTkUserLevel());
        Long tenantId = TenantContextHolder.getTenantId();
        if (!platformAdmin && tenantId != null
                && user.getTenantId() != null && !tenantId.equals(user.getTenantId())) {
            log.warn("[getCurrentScope][userId({}) tokenTenantId({}) contextTenantId({}) blocked]",
                    userId, user.getTenantId(), tenantId);
            throw exception(TK_FORBIDDEN_COMPANY_DATA);
        }
        Long resolvedTenantId = platformAdmin ? getVisitTenantId() : (tenantId == null ? user.getTenantId() : tenantId);
        if (!platformAdmin && (resolvedTenantId == null || resolvedTenantId <= 0)) {
            throw exception(TK_USER_SCOPE_NOT_CONFIGURED);
        }
        return new TkUserScope(userId, resolvedTenantId,
                user.getTkUserLevel(), user.getTkCompanyId());
    }

    @Override
    public Long getWritableCompanyId(Long requestedCompanyId) {
        TkUserScope scope = getCurrentScope();
        if (scope.isPlatformAdmin()) {
            return requestedCompanyId == null ? scope.getTenantId() : requestedCompanyId;
        }
        return scope.getTenantId();
    }

    @Override
    public void validateReadable(Long companyId) {
        getCurrentScope();
    }

    @Override
    public void validateReadable(Long tenantId, Long companyId, String creator) {
        TkUserScope scope = getCurrentScope();
        if (!scope.isGlobalPlatformView() && tenantId != null && !tenantId.equals(scope.getTenantId())) {
            log.warn("[validateReadable][userId({}) tenantId({}) blocked]", scope.getUserId(), tenantId);
            throw exception(TK_FORBIDDEN_COMPANY_DATA);
        }
        if (!scope.canReadAllTenantRecords() && creator != null && !creator.equals(scope.getUserIdString())) {
            log.warn("[validateReadable][userId({}) creator({}) blocked]", scope.getUserId(), creator);
            throw exception(TK_FORBIDDEN_COMPANY_DATA);
        }
    }

    @Override
    public void validateWritable(Long companyId) {
        getCurrentScope();
    }

    @Override
    public void validateWritable(Long tenantId, Long companyId) {
        TkUserScope scope = getCurrentScope();
        if (!scope.isGlobalPlatformView() && tenantId != null && !tenantId.equals(scope.getTenantId())) {
            log.warn("[validateWritable][userId({}) tenantId({}) blocked]", scope.getUserId(), tenantId);
            throw exception(TK_FORBIDDEN_WRITE_COMPANY_DATA);
        }
        validateWritable(companyId);
    }

    @Override
    public void validatePlatformAdmin() {
        TkUserScope scope = getCurrentScope();
        if (!scope.isPlatformAdmin()) {
            throw exception(TK_FORBIDDEN_WRITE_COMPANY_DATA);
        }
    }

    private Long getVisitTenantId() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes)) {
            return null;
        }
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes.getRequest();
        return WebFrameworkUtils.getVisitTenantId(request);
    }

}

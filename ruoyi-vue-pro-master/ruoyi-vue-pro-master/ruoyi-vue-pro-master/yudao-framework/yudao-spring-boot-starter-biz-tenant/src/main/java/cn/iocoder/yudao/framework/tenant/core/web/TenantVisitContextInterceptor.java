package cn.iocoder.yudao.framework.tenant.core.web;

import cn.hutool.core.util.ObjUtil;
import cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.web.core.util.WebFrameworkUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception0;

@RequiredArgsConstructor
@Slf4j
public class TenantVisitContextInterceptor implements HandlerInterceptor {

    private static final String INFO_KEY_TK_USER_LEVEL = "tkUserLevel";
    private static final String TK_PLATFORM_ADMIN = "PLATFORM_ADMIN";

    private final TenantProperties tenantProperties;

    private final SecurityFrameworkService securityFrameworkService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 如果和当前租户编号一致，则直接跳过
        Long visitTenantId = WebFrameworkUtils.getVisitTenantId(request);
        if (visitTenantId == null) {
            return true;
        }
        if (ObjUtil.equal(visitTenantId, TenantContextHolder.getTenantId())) {
            return true;
        }
        // 必须是登录用户
        LoginUser loginUser = SecurityFrameworkUtils.getLoginUser();
        if (loginUser == null) {
            return true;
        }

        // TK 业务要求：只有 PLATFORM_ADMIN 是最高权限管理员，才允许跨租户访问。
        if (!isTkPlatformAdmin(loginUser)) {
            throw exception0(GlobalErrorCodeConstants.FORBIDDEN.getCode(), "您无权切换租户");
        }

        // 【重点】切换租户编号
        loginUser.setVisitTenantId(visitTenantId);
        TenantContextHolder.setTenantId(visitTenantId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 【重点】清理切换，换回原租户编号
        LoginUser loginUser = SecurityFrameworkUtils.getLoginUser();
        if (loginUser != null && loginUser.getTenantId() != null) {
            TenantContextHolder.setTenantId(loginUser.getTenantId());
        }
    }

    private boolean isTkPlatformAdmin(LoginUser loginUser) {
        return loginUser.getInfo() != null
                && TK_PLATFORM_ADMIN.equals(loginUser.getInfo().get(INFO_KEY_TK_USER_LEVEL));
    }

}

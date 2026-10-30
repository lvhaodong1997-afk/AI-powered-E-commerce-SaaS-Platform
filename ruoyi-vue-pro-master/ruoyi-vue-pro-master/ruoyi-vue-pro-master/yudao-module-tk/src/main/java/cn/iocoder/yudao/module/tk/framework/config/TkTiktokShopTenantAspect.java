package cn.iocoder.yudao.module.tk.framework.config;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.tk.service.scope.TkDataScopeService;
import cn.iocoder.yudao.module.tk.service.scope.TkUserScope;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** Establishes Shop's explicit tenant boundary when the global tenant web filters are absent. */
@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class TkTiktokShopTenantAspect {
    private final TkDataScopeService scope;

    public TkTiktokShopTenantAspect(TkDataScopeService scope) { this.scope=scope; }

    @Around("execution(public * cn.iocoder.yudao.module.tk.controller.admin.tiktokshop.TkTiktokShop*Controller.*(..))"
            + " && !execution(* cn.iocoder.yudao.module.tk.controller.admin.tiktokshop.TkTiktokShopAuthController.capabilities(..))"
            + " && !execution(* cn.iocoder.yudao.module.tk.controller.admin.tiktokshop.TkTiktokShopAuthController.callback(..))")
    public Object withTenant(ProceedingJoinPoint invocation) throws Throwable {
        // Resolve ownership from the authenticated user's existing TK scope, never from tenant-id alone.
        TkUserScope current=scope.getCurrentScope();
        Long previous=TenantContextHolder.getTenantId();
        boolean ignored=TenantContextHolder.isIgnore();
        if(current==null || current.getUserId()==null || !current.hasTenantScope() || ignored
                || (previous!=null && !Objects.equals(previous,current.getTenantId()))) {
            throw new IllegalArgumentException("Shop tenant context unavailable");
        }
        try {
            TenantContextHolder.setTenantId(current.getTenantId());
            return invocation.proceed();
        } finally {
            TenantContextHolder.setTenantId(previous);
            TenantContextHolder.setIgnore(ignored);
        }
    }
}

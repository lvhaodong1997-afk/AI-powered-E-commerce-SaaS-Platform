package cn.iocoder.yudao.module.tk.service.channel;

import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiContext;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiException;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * X OpenAPI access is HMAC client isolation plus an explicit x.auth / x.publish
 * permission and a default-off master switch. Grants, tenant, company, owner
 * and endUser are not authorization boundaries.
 */
@Component
@RequiredArgsConstructor
public class TkOpenXAccess {
    private final TkOpenXProperties properties;

    public String requireClient(String permission) {
        if (!properties.isEnabled()) {
            throw TkOpenApiException.unavailable("X_OPEN_API_DISABLED", "X OpenAPI is disabled");
        }
        TkOpenApiPrincipal principal = TkOpenApiContext.getRequiredPrincipal();
        if (principal == null || principal.getClientId() == null || principal.getClientId().isEmpty()) {
            throw TkOpenApiException.unauthorized("X_CLIENT_UNAUTHORIZED", "X OpenAPI client is not authenticated");
        }
        if (permission != null && !principal.hasPermission(permission)) {
            throw TkOpenApiException.forbidden("X_PERMISSION_DENIED", "client is not granted " + permission);
        }
        return principal.getClientId();
    }

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    public boolean isAllowInsecureLoopbackReturnUrl() {
        return properties.isAllowInsecureLoopbackReturnUrl();
    }
}
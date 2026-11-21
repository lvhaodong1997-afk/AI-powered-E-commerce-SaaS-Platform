package cn.iocoder.yudao.module.tk.service.channel;

import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiContext;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiException;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * X OpenAPI access is HMAC client isolation plus an explicit x.auth / x.publish
 * permission and a default-off master switch. The signed request target
 * optionally scopes resources to a workspace; endUserId remains metadata.
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

    /** Read only the query string that the HMAC filter verified, never form data or headers. */
    public String workspaceId() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes)) return null;
        String query = ((ServletRequestAttributes) attributes).getRequest().getQueryString();
        if (query == null) return null;
        String workspace = null;
        for (String part : query.split("&")) {
            if ("workspaceId".equals(part) || part.startsWith("workspaceId=")) {
                String value = part.equals("workspaceId") ? "" : part.substring("workspaceId=".length());
                if (workspace != null || !value.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")) {
                    throw TkOpenApiException.badRequest("X_WORKSPACE_INVALID", "workspaceId must be one lowercase UUID");
                }
                workspace = value;
            }
        }
        return workspace;
    }

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    public boolean isAllowInsecureLoopbackReturnUrl() {
        return properties.isAllowInsecureLoopbackReturnUrl();
    }
}
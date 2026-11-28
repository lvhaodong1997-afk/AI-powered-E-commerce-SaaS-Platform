package cn.iocoder.yudao.module.tk.service.open.meta;

import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenApiClientDO;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenApiClientMapper;
import cn.iocoder.yudao.module.tk.framework.openapi.*;
import cn.iocoder.yudao.module.tk.service.social.auth.TkSocialProperties;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
@RequiredArgsConstructor
public class TkOpenMetaAccess {
    public static final String ROOT = "/admin-api/tk/open/v1/";
    public static final String FACEBOOK = "facebook";
    public static final String INSTAGRAM = "instagram";
    private final Settings settings;
    private final TkSocialProperties social;
    private final TkOpenApiClientMapper clients;

    public boolean enabled(String platform) {
        return social.isEnabled() && (FACEBOOK.equals(platform) ? settings.facebookEnabled
                : INSTAGRAM.equals(platform) && settings.instagramEnabled);
    }

    public Scope require(String platform, String operation) {
        TkOpenApiPrincipal principal = TkOpenApiContext.getPrincipal();
        if (principal == null) throw TkOpenApiException.unauthorized("OPEN_API_AUTH_HEADER_MISSING", "client is not authenticated");
        if (!principal.hasPermission(platform + "." + operation))
            throw TkOpenApiException.forbidden("OPEN_API_PERMISSION_DENIED", "Meta permission is not granted");
        if (!enabled(platform)) throw TkOpenApiException.unavailable("META_OPEN_API_DISABLED", "Meta OpenAPI is not enabled");
        ServletRequestAttributes request = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        String query = request == null ? null : request.getRequest().getQueryString();
        return new Scope(principal.getClientId(), workspace(query), platform);
    }

    static String workspace(String query) {
        if (query == null || !query.matches("workspaceId=[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}"))
            throw TkOpenApiException.badRequest("META_WORKSPACE_INVALID", "Supply one lowercase workspaceId in the signed query");
        return query.substring(12);
    }

    boolean allowed(String clientId, String platform, String operation) {
        TkOpenApiClientDO c = clients.selectByClientId(clientId);
        return enabled(platform) && c != null && Integer.valueOf(0).equals(c.getStatus())
                && new TkOpenApiPrincipal(clientId, c.getClientName(), c.getPermissions()).hasPermission(platform + "." + operation);
    }

    static String provider(String platform) { return FACEBOOK.equals(platform) ? "FACEBOOK_PAGE" : "INSTAGRAM"; }

    String baseUrl() {
        try {
            URI url = URI.create(settings.publicOrigin);
            if (!"https".equals(url.getScheme()) || url.getHost() == null || url.getRawUserInfo() != null
                    || url.getQuery() != null || url.getFragment() != null || !url.getPath().matches("/?")) throw new IllegalArgumentException();
            return "https://" + url.getRawAuthority();
        } catch (RuntimeException ex) {
            throw TkOpenApiException.unavailable("META_MEDIA_UNAVAILABLE", "Meta public origin is not configured");
        }
    }

    Path privateRoot() {
        try {
            Path path = Paths.get(settings.privateRoot).normalize();
            if (!path.isAbsolute()) throw new IllegalArgumentException();
            return path;
        } catch (RuntimeException ex) {
            throw TkOpenApiException.unavailable("META_MEDIA_UNAVAILABLE", "Meta private storage is not configured");
        }
    }

    @RequiredArgsConstructor
    static class Scope {
        final String clientId, workspaceId, platform;
        Object[] args(Object id) { return new Object[]{id, clientId, workspaceId, platform}; }
    }

    @Data @Component @ConfigurationProperties(prefix = "tk.meta-open-api")
    public static class Settings {
        private boolean facebookEnabled, instagramEnabled;
        private String privateRoot;
        private String publicOrigin;
    }
}

package cn.iocoder.yudao.module.tk.service.channel;

import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenApiClientDO;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenApiClientMapper;
import cn.iocoder.yudao.module.tk.framework.openapi.*;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
@RequiredArgsConstructor
public class TkOpenYouTubeAccess {
    public static final String ROOT = "/admin-api/tk/open/v1/youtube";
    private final Settings settings;
    private final TkChannelProperties channels;
    private final TkOpenApiClientMapper clients;

    public boolean isEnabled() { return settings.isEnabled() && channels.ready(TkChannelProperties.YOUTUBE); }

    public void requireEnabled() {
        if (!isEnabled()) throw TkOpenApiException.unavailable("YOUTUBE_OPEN_API_DISABLED", "YouTube OpenAPI is not configured or enabled");
    }

    public String requireClient(String permission) {
        requireEnabled();
        TkOpenApiPrincipal principal = TkOpenApiContext.getPrincipal();
        if (principal == null) throw TkOpenApiException.unauthorized("OPEN_API_AUTH_HEADER_MISSING", "client is not authenticated");
        if (!principal.hasPermission(permission)) throw TkOpenApiException.forbidden("YOUTUBE_PERMISSION_DENIED", "client is not granted " + permission);
        return principal.getClientId();
    }

    public boolean allowed(String clientId, String permission) {
        TkOpenApiClientDO client = clients.selectByClientId(clientId);
        return client != null && Integer.valueOf(0).equals(client.getStatus())
                && new TkOpenApiPrincipal(clientId, client.getClientName(), client.getPermissions()).hasPermission(permission);
    }

    public String workspaceId() {
        ServletRequestAttributes request = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        String query = request == null ? null : request.getRequest().getQueryString();
        String workspace = null;
        if (query != null) for (String part : query.split("&")) {
            if (!part.equals("workspaceId") && !part.startsWith("workspaceId=")) continue;
            String value = part.equals("workspaceId") ? "" : part.substring(12);
            if (workspace != null || !value.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}"))
                throw invalidWorkspace();
            workspace = value;
        }
        if (workspace == null) throw invalidWorkspace();
        return workspace;
    }

    TkChannelProperties.Credentials credentials() {
        requireEnabled();
        TkChannelProperties.Credentials source = channels.getYoutube(), cfg = new TkChannelProperties.Credentials();
        cfg.setEnabled(true);
        cfg.setClientId(source.getClientId());
        cfg.setClientSecret(source.getClientSecret());
        cfg.setRedirectUri(org.springframework.util.StringUtils.hasText(settings.getRedirectUri())
                ? settings.getRedirectUri() : source.getRedirectUri());
        java.net.URI redirect = java.net.URI.create(cfg.getRedirectUri());
        if (!"https".equals(redirect.getScheme()) || redirect.getHost() == null || redirect.getUserInfo() != null
                || redirect.getQuery() != null || redirect.getFragment() != null
                || !(redirect.getPath().equals(ROOT + "/auth/callback")
                || redirect.getPath().equals("/admin-api/tk/channel-publish/youtube/callback")))
            throw TkOpenApiException.unavailable("YOUTUBE_OPEN_API_DISABLED", "YouTube callback URI is not configured");
        return cfg;
    }

    private static TkOpenApiException invalidWorkspace() {
        return TkOpenApiException.badRequest("YOUTUBE_WORKSPACE_INVALID", "workspaceId must be one lowercase UUID in the signed query");
    }

    @Data @Component @ConfigurationProperties(prefix = "tk.youtube-open-api")
    public static class Settings {
        private boolean enabled;
        private String redirectUri;
    }
}

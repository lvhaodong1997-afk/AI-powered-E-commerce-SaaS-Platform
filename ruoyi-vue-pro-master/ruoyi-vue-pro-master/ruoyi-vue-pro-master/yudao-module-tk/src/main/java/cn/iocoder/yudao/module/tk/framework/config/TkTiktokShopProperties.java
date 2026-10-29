package cn.iocoder.yudao.module.tk.framework.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.net.URI;

/** Shop configuration is internal only; expose an explicit capability DTO to clients. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "tk.tiktok-shop")
public class TkTiktokShopProperties {
    private boolean enabled = false;
    private String appKey;
    private String appSecret;
    private String redirectUri;
    private String frontendOrigin;
    private String market = "US";
    private String tokenEncryptionKey;
    private int workers = 2;

    public boolean isConfigured() {
        return enabled && hasText(appKey) && hasText(appSecret)
                && validUrl(redirectUri, true) && validUrl(frontendOrigin, false)
                && "US".equals(market) && hasValidEncryptionKey();
    }

    public boolean hasValidEncryptionKey() {
        try {
            return tokenEncryptionKey != null && java.util.Base64.getDecoder().decode(tokenEncryptionKey).length == 32;
        } catch (IllegalArgumentException ignored) { return false; }
    }

    public String callbackOrigin() {
        if (!validUrl(redirectUri, true)) return null;
        URI uri = URI.create(redirectUri);
        int port = uri.getPort();
        return "https://" + uri.getHost().toLowerCase(java.util.Locale.ROOT)
                + (port == -1 || port == 443 ? "" : ":" + port);
    }

    public void requireConfigured() {
        if (!isConfigured()) {
            throw new IllegalStateException("TikTok Shop configuration is disabled or incomplete");
        }
    }

    private boolean validUrl(String value, boolean callback) {
        if (!hasText(value)) return false;
        try {
            URI uri = URI.create(value);
            boolean schemeAllowed = "https".equalsIgnoreCase(uri.getScheme())
                    || (!callback && "http".equalsIgnoreCase(uri.getScheme()));
            return schemeAllowed && hasText(uri.getHost()) && uri.getUserInfo() == null
                    && uri.getQuery() == null && uri.getFragment() == null
                    && (uri.getPort() == -1 || uri.getPort() > 0 && uri.getPort() <= 65535)
                    && (callback || uri.getPath() == null || uri.getPath().isEmpty());
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private boolean hasText(String value) { return value != null && !value.trim().isEmpty(); }
}

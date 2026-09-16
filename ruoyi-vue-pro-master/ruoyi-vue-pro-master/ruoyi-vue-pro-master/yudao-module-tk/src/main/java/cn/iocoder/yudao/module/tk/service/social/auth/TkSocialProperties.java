package cn.iocoder.yudao.module.tk.service.social.auth;

import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "tk.social")
public class TkSocialProperties {
    private boolean enabled;
    private String graphVersion = "v25.0";
    @ToString.Exclude private String encryptionKey;
    private int sessionTtlMinutes = 10;
    private ProxySettings proxy = new ProxySettings();
    private Credentials instagram = new Credentials();
    private Credentials facebook = new Credentials();

    @Data
    public static class ProxySettings {
        private boolean enabled;
        private String host;
        private int port;
    }

    @Data
    public static class Credentials {
        private String appId;
        @ToString.Exclude private String appSecret;
        private String redirectUri;
        private String configId;
    }

    public void requireEnabled() {
        if (!enabled) throw new IllegalStateException("Meta 社交发布未启用");
    }

    public String version() {
        if (graphVersion == null || !graphVersion.matches("v[0-9]{1,3}\\.[0-9]{1,2}"))
            throw new IllegalStateException("Meta Graph API 版本配置无效");
        return graphVersion;
    }

    public Credentials credentials(String platform) {
        requireEnabled();
        Credentials c;
        if ("INSTAGRAM".equals(platform)) c = instagram;
        else if ("FACEBOOK_PAGE".equals(platform)) c = facebook;
        else throw new IllegalArgumentException("不支持的平台");
        if (blank(c.appId) || blank(c.appSecret) || blank(c.redirectUri)
                || !c.redirectUri.startsWith("https://")
                || ("FACEBOOK_PAGE".equals(platform) && blank(c.configId)))
            throw new IllegalStateException("Meta 授权配置不完整");
        return c;
    }

    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
}

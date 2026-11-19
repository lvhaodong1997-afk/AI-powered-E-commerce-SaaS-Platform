package cn.iocoder.yudao.module.tk.service.channel;

import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Data
@Component
@ConfigurationProperties(prefix = "tk.channel-publish")
public class TkChannelProperties {
    public static final String X = "x";
    public static final String YOUTUBE = "youtube";
    private Credentials x = new Credentials();
    private Credentials youtube = new Credentials();

    @Data
    public static class Credentials {
        private boolean enabled;
        private String clientId;
        @ToString.Exclude private String clientSecret;
        private String redirectUri;

        boolean ready() {
            return enabled && StringUtils.hasText(clientId) && StringUtils.hasText(clientSecret)
                    && redirectUri != null && redirectUri.startsWith("https://");
        }
    }

    public static boolean supports(String name) { return X.equals(name) || YOUTUBE.equals(name); }
    public Credentials config(String name) {
        if (X.equals(name)) return x;
        if (YOUTUBE.equals(name)) return youtube;
        throw new IllegalArgumentException("Unsupported publishing channel");
    }
    public boolean ready(String name) { return config(name).ready(); }
    Credentials require(String name) {
        Credentials config = config(name);
        if (!config.ready()) throw new IllegalArgumentException("Channel publishing is not configured");
        return config;
    }
}

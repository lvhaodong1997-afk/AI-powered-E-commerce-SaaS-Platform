package cn.iocoder.yudao.module.tk.framework.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.net.URI;

@Component
@ConfigurationProperties(prefix = "tk.payment")
@Data
public class TkPaymentProperties {
    private boolean enabled;
    private boolean workerEnabled = true;
    private boolean liveMode = true;
    private String gatewayUrl = "";
    private String gatewaySecret = "";
    private String notifySecret = "";
    private String notifyPath = "/admin-api/tk/payments/notify";

    public boolean isConfigured() {
        try {
            URI uri = URI.create(gatewayUrl);
            return "https".equals(uri.getScheme()) && uri.getHost() != null && uri.getUserInfo() == null
                    && uri.getQuery() == null && uri.getFragment() == null && (uri.getPort() == -1 || uri.getPort() == 443)
                    && gatewaySecret != null && gatewaySecret.length() >= 32
                    && notifySecret != null && notifySecret.length() >= 32;
        } catch (IllegalArgumentException e) { return false; }
    }
}

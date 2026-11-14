package cn.iocoder.yudao.module.tk.framework.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.time.Instant;

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
    private boolean verifyEnabled;
    private String verifyExpiresAt = "";
    private String verifyTenantIds = "";
    private String verifyUserIds = "";

    public boolean isVerificationAllowed(Long tenantId, Long userId) {
        if (!verifyEnabled || !containsId(verifyTenantIds, tenantId) || !containsId(verifyUserIds, userId)) return false;
        try {
            return verifyExpiresAt != null && Instant.parse(verifyExpiresAt).isAfter(Instant.now());
        } catch (java.time.DateTimeException e) { return false; }
    }

    private boolean containsId(String csv, Long id) {
        if (csv == null || id == null || id <= 0) return false;
        boolean found = false;
        for (String entry : csv.split(",", -1)) {
            String value = entry.trim();
            if (!value.matches("[1-9][0-9]{0,17}")) return false;
            if (value.equals(id.toString())) found = true;
        }
        return found;
    }

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

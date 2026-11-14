package cn.iocoder.yudao.module.system.service.tenant;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.util.Collections;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "tk.registration")
public class TenantRegistrationProperties {
    private boolean enabled;
    private Long defaultPackageId;
    private Integer validityDays;
    private Integer validityYears = 0;
    private int memberLimit = 1;
    private String phoneBaseUrl;
    private String phoneSecret;
    private String inviteBaseUrl;
    private List<String> trustedProxyIps = Collections.emptyList();

    public boolean tenantAvailable() {
        return enabled && defaultPackageId != null && defaultPackageId > 0
                && validDuration() && memberLimit > 0;
    }

    private boolean validDuration() {
        if (validityYears != null && validityYears != 0) { return validityYears > 0 && validityYears <= 100; }
        return validityDays != null && validityDays > 0 && validityDays <= 36600;
    }

    public boolean smsAvailable() {
        if (!tenantAvailable() || phoneSecret == null || phoneSecret.length() < 32 || phoneBaseUrl == null) {
            return false;
        }
        try {
            URI uri = URI.create(phoneBaseUrl);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                    && uri.getUserInfo() == null && uri.getQuery() == null && uri.getFragment() == null
                    && (uri.getPath().isEmpty() || "/".equals(uri.getPath()));
        } catch (IllegalArgumentException e) { return false; }
    }
}

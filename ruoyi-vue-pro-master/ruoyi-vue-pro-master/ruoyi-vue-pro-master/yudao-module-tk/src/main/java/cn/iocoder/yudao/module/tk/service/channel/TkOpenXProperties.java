package cn.iocoder.yudao.module.tk.service.channel;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "tk.x-open-api")
public class TkOpenXProperties {
    private boolean enabled;
    private List<Grant> grants = Collections.emptyList();

    @Data
    public static class Grant {
        private String clientId;
        private Long tenantId;
        private Long companyId;
        /** Optional. When set together with xUserId, the client stays in single-account pilot mode. */
        private Long ownerUserId;
        /** Optional designated X numeric user id for pilot mode. */
        private String xUserId;

        public boolean hasOwner() {
            return ownerUserId != null && ownerUserId > 0;
        }

        public boolean hasXUser() {
            return xUserId != null && xUserId.matches("[0-9]{1,30}");
        }

        public boolean isPilot() {
            return hasOwner() && hasXUser();
        }

        public boolean hasPartialPin() {
            boolean owner = ownerUserId != null;
            boolean xUser = xUserId != null && !xUserId.trim().isEmpty();
            return owner ^ xUser || (xUser && !hasXUser()) || (owner && !hasOwner());
        }

        public boolean hasValidScope() {
            return clientId != null && !clientId.trim().isEmpty()
                    && tenantId != null && tenantId > 0
                    && companyId != null && companyId > 0
                    && !hasPartialPin();
        }
    }
}

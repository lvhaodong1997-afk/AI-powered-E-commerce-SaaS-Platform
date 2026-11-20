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
        private Long ownerUserId;
        private String xUserId;
    }
}

package cn.iocoder.yudao.module.tk.service.social.stats;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix="tk.social.stats")
public class TkSocialStatsProperties {
    /** Apply the additive statistics migration before enabling. */
    private boolean enabled;
    private int seedBatchSize=100;
    private int leaseSeconds=600;
}

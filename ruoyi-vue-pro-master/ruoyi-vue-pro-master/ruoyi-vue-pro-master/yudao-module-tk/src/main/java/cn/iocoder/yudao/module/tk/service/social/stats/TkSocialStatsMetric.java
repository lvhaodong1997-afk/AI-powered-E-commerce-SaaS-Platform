package cn.iocoder.yudao.module.tk.service.social.stats;

import lombok.Data;
import java.time.LocalDateTime;

/** A null count is unknown, never an implied zero. fetchedAt belongs to the value. */
@Data
public class TkSocialStatsMetric {
    private String key;
    private Long value;
    private String unit = "count";
    private String sourceMetric;
    private String scope;
    private String period;
    private String availability;
    private LocalDateTime fetchedAt;
    private LocalDateTime lastAttemptTime;
    private LocalDateTime nextAttemptTime;
    private String errorCode;
    private boolean retryable;

    public static TkSocialStatsMetric missing(String key, String source, String scope, String availability) {
        TkSocialStatsMetric metric = new TkSocialStatsMetric();
        metric.key=key; metric.sourceMetric=source; metric.scope=scope; metric.availability=availability;
        if("account".equals(scope)) metric.period="current";
        return metric;
    }
}

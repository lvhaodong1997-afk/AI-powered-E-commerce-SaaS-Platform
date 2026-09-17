package cn.iocoder.yudao.module.tk.controller.admin.social.vo;

import cn.iocoder.yudao.module.tk.service.social.stats.TkSocialStatsMetric;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class TkSocialStatsRespVO {
    private Long objectId;
    private String platform,syncStatus,errorCode,errorMessage;
    private LocalDateTime lastSuccessTime,lastAttemptTime,nextSyncTime;
    private List<TkSocialStatsMetric> metrics;
}

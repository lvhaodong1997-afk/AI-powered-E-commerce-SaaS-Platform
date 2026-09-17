package cn.iocoder.yudao.module.tk.dal.dataobject.social;

import lombok.Data;
import java.time.LocalDateTime;

/** Snapshot shared by the separate account and published-detail statistics tables. */
@Data
public class TkSocialStatsDO {
    private Long id,tenantId,companyId,objectId,socialAccountId;
    private String creator,platform,syncStatus,metricsJson,errorCode,errorMessage,leaseToken;
    private Long followers,following,mediaCount,views,likes,comments,shares,saves,reach;
    private Integer retryCount;
    private LocalDateTime publishedTime,lastAttemptTime,lastSuccessTime,nextSyncTime,lastManualTime,leaseUntil;
}

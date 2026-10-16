package cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class TkOpenTiktokPublishQueueVO {
    private TkOpenTiktokPublishQueueVO() { }

    @Data
    @Schema(description = "TK Open TikTok publish queue summary")
    public static class Summary {
        private LocalDate date;
        private long pendingCount;
        private long runningCount;
        private long uploadingCount;
        private long recoveryCount;
        private long staleCount;
        private long scheduledCount;
        private Long oldestPendingSeconds;
        private LocalDateTime refreshedAt;
        private List<Detail> attention;
    }

    @Data
    @Schema(description = "TK Open TikTok publish queue detail")
    public static class Detail {
        private Long id;
        private String clientId;
        private String taskId;
        private String detailId;
        private String accountName;
        private String taskTitle;
        private String taskStatus;
        private String status;
        private String tiktokStatus;
        private String phase;
        private String publishId;
        private String failReason;
        private String lastError;
        private Integer retryCount;
        private LocalDateTime heartbeatTime;
        private LocalDateTime lastSyncTime;
        private LocalDateTime updateTime;
        private long ageSeconds;
        private boolean stale;
        private boolean canCancel;
        private boolean canRetry;
    }
}

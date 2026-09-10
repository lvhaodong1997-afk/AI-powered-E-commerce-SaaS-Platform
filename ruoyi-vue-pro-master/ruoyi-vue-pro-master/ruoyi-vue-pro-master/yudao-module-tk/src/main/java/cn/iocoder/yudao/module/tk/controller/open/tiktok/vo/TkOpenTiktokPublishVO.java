package cn.iocoder.yudao.module.tk.controller.open.tiktok.vo;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

public final class TkOpenTiktokPublishVO {
    private TkOpenTiktokPublishVO() {}

    @Data
    public static class TaskCreateReq {
        @NotEmpty
        @Size(max = 20)
        private List<String> connectionIds;
        @NotBlank
        private String mediaId;
        @Size(max = 512)
        private String title;
        @Size(max = 2200)
        private String caption;
        @NotBlank
        @Pattern(regexp = "DIRECT_POST|UPLOAD_TO_INBOX")
        private String postMode;
        @NotBlank
        private String privacyLevel;
        private Boolean allowComment;
        private Boolean allowDuet;
        private Boolean allowStitch;
        private Boolean commercialContent;
        private Boolean brandContent;
        private Boolean aigcContent;
        @Size(max = 128)
        private String externalRequestId;
    }

    @Data
    public static class QuickTaskCreateReq {
        @NotBlank
        @Size(max = 128)
        private String externalAccountId;
        @NotBlank
        @Size(max = 2048)
        private String videoUrl;
        @Size(max = 512)
        private String fileName;
        @Size(max = 128)
        private String contentType;
        private Long coverTimestampMs;
        @Size(max = 512)
        private String title;
        @Size(max = 2200)
        private String caption;
        @Pattern(regexp = "DIRECT_POST|UPLOAD_TO_INBOX")
        private String postMode;
        @Size(max = 64)
        private String privacyLevel;
        private Boolean allowComment;
        private Boolean allowDuet;
        private Boolean allowStitch;
        private Boolean commercialContent;
        private Boolean brandContent;
        private Boolean aigcContent;
        @Size(max = 128)
        private String externalRequestId;
    }

    @Data
    public static class TaskResp {
        private String taskId;
        private String mediaId;
        private String externalRequestId;
        private String status;
        private Integer accountCount;
        private Integer successCount;
        private Integer failedCount;
        private Integer pendingCount;
        private String failReason;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    @Data
    public static class DetailResp {
        private String detailId;
        private String taskId;
        private String connectionId;
        private String accountName;
        private String status;
        private String tiktokStatus;
        private String publishId;
        private String publishUrl;
        private String failReason;
        private Integer retryCount;
        private LocalDateTime updateTime;
    }

    @Data
    public static class MetricsResp {
        private String taskId;
        private String detailId;
        private String connectionId;
        private String publishId;
        private String publicPostId;
        private String publishUrl;
        private String metricsStatus;
        private Long viewCount;
        private Long likeCount;
        private Long commentCount;
        private Long shareCount;
        private LocalDateTime metricsLastSyncTime;
        private String metricsFailReason;
    }
}

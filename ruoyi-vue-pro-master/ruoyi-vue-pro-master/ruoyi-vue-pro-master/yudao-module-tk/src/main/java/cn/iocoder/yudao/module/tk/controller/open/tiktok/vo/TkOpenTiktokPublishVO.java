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
}

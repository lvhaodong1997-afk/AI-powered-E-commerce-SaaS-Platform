package cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - TikTok 发布明细 Response VO")
@Data
public class TkTiktokPublishDetailRespVO {

    private Long id;
    private Long tenantId;
    private String businessTraceId;
    private Long companyId;
    private Long publishTaskId;
    private Long generationTaskId;
    private Long accountId;
    private String accountDisplayName;
    private String publishId;
    private String publishUrl;
    private String tiktokStatus;
    private String status;
    private String postMode;
    private String privacyLevel;
    private Boolean allowComment;
    private Boolean allowDuet;
    private Boolean allowStitch;
    private Boolean commercialContent;
    private Boolean brandContent;
    private Boolean aigcContent;
    private String failReason;
    private Integer retryCount;
    private LocalDateTime publishUrlRegisteredTime;
    private LocalDateTime lastSyncTime;
    private LocalDateTime createTime;

}

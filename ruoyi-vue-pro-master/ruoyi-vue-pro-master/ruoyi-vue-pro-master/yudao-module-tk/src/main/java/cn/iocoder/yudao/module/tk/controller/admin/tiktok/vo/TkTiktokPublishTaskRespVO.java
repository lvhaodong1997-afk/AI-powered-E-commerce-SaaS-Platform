package cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - TikTok 发布任务 Response VO")
@Data
public class TkTiktokPublishTaskRespVO {

    private Long id;
    private Long tenantId;
    private String businessTraceId;
    private Long companyId;
    private Long generationTaskId;
    private String title;
    private String caption;
    private String videoUrl;
    private String postMode;
    private String privacyLevel;
    private Integer accountCount;
    private Integer successCount;
    private Integer failedCount;
    private Integer pendingCount;
    private String status;
    private String failReason;
    private LocalDateTime createTime;

}

package cn.iocoder.yudao.module.tk.controller.admin.generation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - TK 智能生成任务摘要 Response VO")
@Data
public class TkGenerationTaskSummaryRespVO {

    private Long id;
    private Long tenantId;
    private String creator;
    private String creatorName;
    private Integer dailyUserVideoNo;
    private String businessTraceId;
    private Long batchId;
    private Integer scriptIndex;
    private Integer videoIndex;
    private Long companyId;
    private String sourceUrl;
    private Long libraryId;
    private String materialPurpose;
    private String productCategoryCode;
    private String generationRouteCode;
    private String ttsProvider;
    private Boolean voiceEnabled;
    private String mimoVoiceMode;
    private Boolean bgmEnabled;
    private Boolean subtitleEnabled;
    private String openingVideoName;
    private Integer referenceDuration;
    private Integer targetDuration;
    private String status;
    private Integer progress;
    private String outputUrl;
    private String failReason;
    private String failCode;
    private String currentStep;
    private Integer retryCount;
    private String workerId;
    private LocalDateTime heartbeatTime;
    private String title;
    private Long latestPublishDetailId;
    private String latestPublishAccountName;
    private String latestPublishUrl;
    private LocalDateTime latestPublishUrlRegisteredTime;
    private LocalDateTime createTime;

}

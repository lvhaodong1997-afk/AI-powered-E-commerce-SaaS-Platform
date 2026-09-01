package cn.iocoder.yudao.module.tk.controller.admin.generation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - TK 智能生成任务状态 Response VO")
@Data
public class TkGenerationTaskStatusRespVO {

    private Long id;
    private Long batchId;
    private Integer scriptIndex;
    private Integer videoIndex;
    private String productCategoryCode;
    private String generationRouteCode;
    private String status;
    private Integer progress;
    private String outputUrl;
    private String failReason;
    private String failCode;
    private String currentStep;
    private String currentStepCode;
    private Integer currentStepCompleted;
    private Integer currentStepTotal;
    private LocalDateTime heartbeatTime;
    private LocalDateTime stepStartedAt;
    private LocalDateTime stepFinishedAt;

}

package cn.iocoder.yudao.module.tk.controller.admin.generation.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TkGenerationStepLogRespVO {

    private Long id;
    private Long taskId;
    private Long batchId;
    private String stepCode;
    private String stepName;
    private String status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long durationMillis;
    private String failCode;
    private String failReason;
    private Integer retryCount;
    private String workerId;
}

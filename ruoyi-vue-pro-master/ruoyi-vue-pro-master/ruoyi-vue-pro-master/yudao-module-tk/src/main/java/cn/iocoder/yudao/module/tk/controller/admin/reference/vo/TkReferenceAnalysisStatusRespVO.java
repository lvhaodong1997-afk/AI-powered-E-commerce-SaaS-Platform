package cn.iocoder.yudao.module.tk.controller.admin.reference.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - TK 对标分析状态 Response VO")
@Data
public class TkReferenceAnalysisStatusRespVO {

    private Long id;
    private String status;
    private String failReason;
    private String productName;
    private Integer videoDuration;
    private String coreSellingPoints;
    private String analysisStageStatus;
    private String sellingPointStageStatus;
    private String scriptStageStatus;
    private Integer sellingPointCount;
    private Integer scriptOptionCount;
    private LocalDateTime updateTime;

}

package cn.iocoder.yudao.module.tk.controller.admin.reference.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - TK 文案方案 Response VO")
@Data
public class TkReferenceScriptOptionRespVO {

    private Long id;
    private Long tenantId;
    private Long analysisId;
    private Long companyId;
    private Long libraryId;
    private Integer optionNo;
    private String title;
    private String points;
    private String displayTitleZh;
    private String displayPointsZh;
    private BigDecimal estimatedConversionRate;
    private String conversionLevel;
    private String scriptText;
    private String segmentTimeline;
    private String displayScriptZh;
    private Boolean selected;
    private LocalDateTime createTime;

}

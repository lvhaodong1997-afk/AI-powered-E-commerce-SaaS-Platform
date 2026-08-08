package cn.iocoder.yudao.module.tk.controller.admin.reference.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - TK 对标分析 Response VO")
@Data
public class TkReferenceAnalysisRespVO {

    private Long id;
    private Long tenantId;
    private String creator;
    private String creatorName;
    private String businessTraceId;
    private Long companyId;
    private Long libraryId;
    private String sourceUrl;
    private String targetLanguage;
    private Integer referenceDuration;
    private String materialPurpose;
    private String analysisProvider;
    private String analysisModel;
    private String sourceDomain;
    private String resolvedVideoUrl;
    private String coverUrl;
    private String productName;
    private Integer videoDuration;
    private String publishTime;
    private String coreSellingPoints;
    private String targetAudience;
    private String usageScenarios;
    private String videoStructure;
    private String analysisResult;
    private String displayAnalysisResultZh;
    private String sellingPoints;
    private String displaySellingPointsZh;
    private String status;
    private String failReason;
    private String analysisStageStatus;
    private String sellingPointStageStatus;
    private String scriptStageStatus;
    private Integer sellingPointCount;
    private Integer scriptOptionCount;
    private List<TkReferenceScriptOptionRespVO> scriptOptions;
    private LocalDateTime createTime;

}

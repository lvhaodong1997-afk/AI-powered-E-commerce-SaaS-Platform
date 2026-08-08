package cn.iocoder.yudao.module.tk.dal.dataobject;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

@TableName("tk_reference_analysis")
@KeySequence("tk_reference_analysis_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TkReferenceAnalysisDO extends TenantBaseDO {

    @TableId
    private Long id;

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

}

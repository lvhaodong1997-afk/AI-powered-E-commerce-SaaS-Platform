package cn.iocoder.yudao.module.tk.dal.dataobject;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;

@TableName("tk_reference_script_option")
@KeySequence("tk_reference_script_option_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TkReferenceScriptOptionDO extends TenantBaseDO {

    @TableId
    private Long id;

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

}

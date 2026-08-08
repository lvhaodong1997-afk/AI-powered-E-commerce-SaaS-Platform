package cn.iocoder.yudao.module.tk.dal.dataobject;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

@TableName("tk_generation_batch")
@KeySequence("tk_generation_batch_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TkGenerationBatchDO extends TenantBaseDO {

    @TableId
    private Long id;

    private String batchNo;

    private String name;

    private Long companyId;

    private Long libraryId;

    private String sourceUrl;

    private String targetLanguage;

    private Integer scriptCount;

    private Integer videosPerScript;

    private Integer expectedVideoCount;

    private Integer createdTaskCount;

    private Integer successTaskCount;

    private Integer failedTaskCount;

    private Integer runningTaskCount;

    private Integer progressPercent;

    private String status;

    private String failSummary;
}

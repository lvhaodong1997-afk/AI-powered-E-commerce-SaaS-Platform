package cn.iocoder.yudao.module.tk.dal.dataobject;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

@TableName("tk_tiktok_publish_task")
@KeySequence("tk_tiktok_publish_task_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TkTiktokPublishTaskDO extends TenantBaseDO {

    @TableId
    private Long id;

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

}

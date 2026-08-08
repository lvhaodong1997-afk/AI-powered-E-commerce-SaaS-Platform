package cn.iocoder.yudao.module.tk.dal.dataobject;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

@TableName("tk_generation_step_log")
@KeySequence("tk_generation_step_log_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TkGenerationStepLogDO extends TenantBaseDO {

    @TableId
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

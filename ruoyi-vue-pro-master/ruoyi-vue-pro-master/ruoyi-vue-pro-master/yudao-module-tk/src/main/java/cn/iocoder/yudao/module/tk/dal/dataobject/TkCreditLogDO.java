package cn.iocoder.yudao.module.tk.dal.dataobject;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

@TableName("tk_credit_log")
@KeySequence("tk_credit_log_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TkCreditLogDO extends TenantBaseDO {

    @TableId
    private Long id;

    private String bizType;

    private Long bizId;

    private String action;

    private Long credits;

    private String status;

    private Long beforeRemainingCredits;

    private Long afterRemainingCredits;

    private Long beforeFrozenCredits;

    private Long afterFrozenCredits;

    private String remark;

}

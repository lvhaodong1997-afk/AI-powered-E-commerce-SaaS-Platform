package cn.iocoder.yudao.module.tk.dal.dataobject;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

@TableName("tk_tenant_credit_account")
@KeySequence("tk_tenant_credit_account_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TkTenantCreditAccountDO extends TenantBaseDO {

    @TableId
    private Long id;

    private Long totalCredits;

    private Long remainingCredits;

    private Long frozenCredits;

    private Long warningThreshold;

}

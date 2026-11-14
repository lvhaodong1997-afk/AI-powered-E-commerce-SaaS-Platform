package cn.iocoder.yudao.module.system.dal.dataobject.tenant;
import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.KeySequence;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data @EqualsAndHashCode(callSuper = true)
@TableName("system_tenant_referral") @KeySequence("system_tenant_referral_seq") @TenantIgnore
public class TenantReferralDO extends BaseDO {
    private Long id;
    private Long inviterTenantId;
    private Long invitedTenantId;
}

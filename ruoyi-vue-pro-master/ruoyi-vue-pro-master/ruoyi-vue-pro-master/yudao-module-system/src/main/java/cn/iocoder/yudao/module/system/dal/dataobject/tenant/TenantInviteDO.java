package cn.iocoder.yudao.module.system.dal.dataobject.tenant;
import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.KeySequence;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data @EqualsAndHashCode(callSuper = true)
@TableName("system_tenant_invite") @KeySequence("system_tenant_invite_seq") @TenantIgnore
public class TenantInviteDO extends BaseDO {
    private Long id;
    private Long tenantId;
    private String inviteCode;
}

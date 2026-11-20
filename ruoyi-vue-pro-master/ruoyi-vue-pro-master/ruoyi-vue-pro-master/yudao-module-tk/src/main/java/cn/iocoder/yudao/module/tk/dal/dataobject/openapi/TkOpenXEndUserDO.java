package cn.iocoder.yudao.module.tk.dal.dataobject.openapi;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TenantIgnore
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tk_open_x_end_user")
public class TkOpenXEndUserDO extends BaseDO {
    @TableId
    private Long id;
    private String clientId;
    private String endUserId;
    private Long tenantId;
    private Long companyId;
    private Long ownerUserId;
}

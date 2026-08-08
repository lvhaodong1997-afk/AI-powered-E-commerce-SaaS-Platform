package cn.iocoder.yudao.module.tk.dal.dataobject;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

@TableName("tk_tiktok_account_group")
@KeySequence("tk_tiktok_account_group_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TkTiktokAccountGroupDO extends TenantBaseDO {

    @TableId
    private Long id;

    private Long companyId;
    private String name;
    private String scene;
    private String labels;
    private String remark;
    private Integer status;

}

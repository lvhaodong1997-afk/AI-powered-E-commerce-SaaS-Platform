package cn.iocoder.yudao.module.tk.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("system_users")
public class TkUserScopeDO {

    @TableId
    private Long id;

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("tk_user_level")
    private String tkUserLevel;

    @TableField("tk_company_id")
    private Long tkCompanyId;

}

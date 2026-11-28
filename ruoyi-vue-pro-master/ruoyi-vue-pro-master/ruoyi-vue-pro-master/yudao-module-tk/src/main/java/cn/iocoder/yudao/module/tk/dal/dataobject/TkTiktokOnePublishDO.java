package cn.iocoder.yudao.module.tk.dal.dataobject;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tk_tiktok_one_publish")
public class TkTiktokOnePublishDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long companyId;
    private Long generationTaskId;
    private String tiktokUsername;
    private String projectName;
    private String anchorName;
    private String videoId;
    private String videoUrl;
    private Boolean projectConfirmed;
    private Boolean anchorConfirmed;
}

package cn.iocoder.yudao.module.tk.dal.dataobject;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

@TableName("tk_tiktok_publish_media")
@KeySequence("tk_tiktok_publish_media_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TkTiktokPublishMediaDO extends TenantBaseDO {
    @TableId
    private Long id;
    private Long companyId;
    private String fileName;
    private String fileUrl;
    private String coverUrl;
    private Long fileSize;
    private String mimeType;
    private String status;
}

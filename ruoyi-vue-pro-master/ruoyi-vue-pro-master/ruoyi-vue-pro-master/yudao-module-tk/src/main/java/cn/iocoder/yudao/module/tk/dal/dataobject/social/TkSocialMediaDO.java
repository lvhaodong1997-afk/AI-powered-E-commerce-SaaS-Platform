package cn.iocoder.yudao.module.tk.dal.dataobject.social;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tk_social_media")
public class TkSocialMediaDO extends TenantBaseDO {
    @TableId
    private Long id;
    private Long companyId;
    private Long fileSize;
    private String mediaType;
    private String fileName;
    private String contentType;
    private String objectKey;
    private String publicUrl;
    private String status;
    private Integer width;
    private Integer height;
    private Double durationSeconds;
    private Double frameRate;
}

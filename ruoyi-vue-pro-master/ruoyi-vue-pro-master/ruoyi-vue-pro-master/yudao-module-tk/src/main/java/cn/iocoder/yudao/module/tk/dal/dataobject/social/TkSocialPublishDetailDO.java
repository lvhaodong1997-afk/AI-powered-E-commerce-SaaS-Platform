package cn.iocoder.yudao.module.tk.dal.dataobject.social;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tk_social_publish_detail")
public class TkSocialPublishDetailDO extends TenantBaseDO {
    @TableId
    private Long id;
    private Long companyId;
    private Long publishTaskId;
    private Long socialAccountId;
    private String platform;
    private String accountName;
    private String status;
    private String platformStatus;
    private String externalContainerId;
    private String externalMediaId;
    private String externalPostId;
    private String publishUrl;
    private String leaseToken;
    private String errorCode;
    private String errorMessage;
    private Integer retryCount;
    private Integer pollCount;
    private LocalDateTime nextRetryTime;
    private LocalDateTime leaseUntil;
    private LocalDateTime lastSyncTime;
    private LocalDateTime publishedTime;
}

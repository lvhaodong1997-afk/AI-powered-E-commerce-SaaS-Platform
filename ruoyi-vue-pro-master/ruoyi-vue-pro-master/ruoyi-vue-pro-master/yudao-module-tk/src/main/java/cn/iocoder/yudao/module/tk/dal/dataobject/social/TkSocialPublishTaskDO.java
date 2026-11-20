package cn.iocoder.yudao.module.tk.dal.dataobject.social;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tk_social_publish_task")
public class TkSocialPublishTaskDO extends TenantBaseDO {
    @TableId
    private Long id;
    private Long companyId;
    private Long mediaId;
    private Long openXConnectionId;
    private Long openXGenerationTaskId;
    private String openXTaskId;
    private String openEndUserId;
    private String title;
    private String instagramCaption;
    private String facebookMessage;
    private String channel;
    private String channelText;
    private String channelPrivacy;
    private Boolean channelForKids;
    private Boolean channelSynthetic;
    private String status;
    private String idempotencyKey;
    private String requestHash;
    private Integer targetCount;
    private Integer successCount;
    private Integer failedCount;
    private Integer pendingCount;
}

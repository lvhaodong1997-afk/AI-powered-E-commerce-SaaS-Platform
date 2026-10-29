package cn.iocoder.yudao.module.tk.dal.dataobject.tiktokshop;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import java.time.LocalDateTime;

@TableName("tk_tiktok_shop_publish_task")
@KeySequence("tk_tiktok_shop_publish_task_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class TkTiktokShopPublishTaskDO extends TenantBaseDO {
    @TableId
    private Long id;
    private Long companyId;
    private Long accountId;
    private Long generationTaskId;
    private Long mediaId;
    private String sourcePath;
    private String sourceFingerprint;
    private String fileName;
    private Long fileSize;
    private String title;
    private String productId;
    private String productLinkTitle;
    private String productSnapshot;
    private String idempotencyKey;
    private String requestHash;
    private String stage;
    private String fileId;
    private String precheckTaskId;
    private String precheckResult;
    private Integer version;
    private Integer confirmedVersion;
    private Long confirmedBy;
    private LocalDateTime confirmedAt;
    private String videoId;
    private String requestId;
    private String ownerToken;
    private LocalDateTime leaseExpireTime;
    private LocalDateTime nextPollTime;
    private Integer pollCount;
    private String failCode;
    private String failReason;
}
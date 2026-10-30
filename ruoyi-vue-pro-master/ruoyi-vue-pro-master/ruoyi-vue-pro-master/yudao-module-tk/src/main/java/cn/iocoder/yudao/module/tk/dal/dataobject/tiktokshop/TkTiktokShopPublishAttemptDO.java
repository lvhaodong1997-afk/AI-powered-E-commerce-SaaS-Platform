package cn.iocoder.yudao.module.tk.dal.dataobject.tiktokshop;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import java.time.LocalDateTime;

@TableName("tk_tiktok_shop_publish_attempt")
@KeySequence("tk_tiktok_shop_publish_attempt_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class TkTiktokShopPublishAttemptDO extends TenantBaseDO {
    @TableId
    private Long id;
    private Long companyId;
    private Long taskId;
    private Integer attemptNo;
    private String requestHash;
    private String stage;
    private String requestId;
    private String videoId;
    private String responseJson;
    private String ownerToken;
    private LocalDateTime leaseExpireTime;
    private String failCode;
    private String failReason;
}
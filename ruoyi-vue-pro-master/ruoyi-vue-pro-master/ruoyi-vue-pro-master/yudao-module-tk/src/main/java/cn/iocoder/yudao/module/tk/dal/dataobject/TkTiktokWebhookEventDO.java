package cn.iocoder.yudao.module.tk.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

@TenantIgnore
@TableName("tk_tiktok_webhook_event")
@KeySequence("tk_tiktok_webhook_event_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TkTiktokWebhookEventDO extends BaseDO {

    @TableId
    private Long id;

    private String eventId;
    private String clientKey;
    private String userOpenId;
    private String eventType;
    private String publishId;
    private String postId;
    private String payloadJson;
    private String status;
    private String failReason;
    private LocalDateTime receivedTime;
    private LocalDateTime processedTime;

}

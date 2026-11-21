package cn.iocoder.yudao.module.tk.dal.dataobject.openapi;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@TenantIgnore
@TableName("tk_open_x_publish_task")
@KeySequence("tk_open_x_publish_task_seq")
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TkOpenXPublishTaskDO extends BaseDO {
    @TableId
    private Long id;
    private String taskId;
    private String clientId;
    private String workspaceId;
    private String connectionId;
    private String mediaId;
    private String text;
    private String endUserId;
    private String idempotencyKey;
    private String payloadHash;
    private String status;
    private String failCode;
    private String failReason;
    private String tweetId;
    private String publishUrl;
    private String platformStatus;
    private String externalContainerId;
    private Long uploadOffset;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime nextRetryTime;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String leaseToken;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime leaseUntil;
    private Integer pollCount;
}

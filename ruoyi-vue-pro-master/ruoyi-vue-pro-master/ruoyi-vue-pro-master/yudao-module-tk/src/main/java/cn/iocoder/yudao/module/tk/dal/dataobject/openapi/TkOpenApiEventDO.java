package cn.iocoder.yudao.module.tk.dal.dataobject.openapi;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@TenantIgnore
@TableName("tk_open_api_event")
@KeySequence("tk_open_api_event_seq")
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TkOpenApiEventDO extends BaseDO {
    @TableId
    private Long id;
    private String eventId;
    private String clientId;
    private String eventType;
    private String resourceType;
    private String resourceId;
    private String callbackUrl;
    private String payloadJson;
    private String status;
    private Integer attemptCount;
    private LocalDateTime nextRetryTime;
    private Integer lastHttpStatus;
    private String lastError;
    private LocalDateTime deliveredTime;
}

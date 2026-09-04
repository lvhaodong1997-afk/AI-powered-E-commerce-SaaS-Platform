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
@TableName("tk_open_api_idempotency")
@KeySequence("tk_open_api_idempotency_seq")
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TkOpenApiIdempotencyDO extends BaseDO {
    @TableId
    private Long id;
    private String clientId;
    private String idempotencyKey;
    private String requestHash;
    private String resourceType;
    private String resourceId;
    private String status;
    private LocalDateTime expireTime;
}

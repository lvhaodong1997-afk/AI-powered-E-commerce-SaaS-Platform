package cn.iocoder.yudao.module.tk.dal.dataobject.openapi;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;
import lombok.experimental.Accessors;

import java.time.LocalDate;

@TenantIgnore
@TableName("tk_open_api_request_log")
@KeySequence("tk_open_api_request_log_seq")
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TkOpenApiRequestLogDO extends BaseDO {
    @TableId
    private Long id;
    private String requestId;
    private String clientId;
    private String httpMethod;
    private String requestTarget;
    private Integer httpStatus;
    private String errorCode;
    private Long durationMs;
    private String clientIp;
    private LocalDate requestDate;
}

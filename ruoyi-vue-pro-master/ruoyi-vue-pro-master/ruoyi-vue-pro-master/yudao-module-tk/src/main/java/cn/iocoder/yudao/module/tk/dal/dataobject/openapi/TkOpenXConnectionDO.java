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
@TableName("tk_open_x_connection")
@KeySequence("tk_open_x_connection_seq")
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TkOpenXConnectionDO extends BaseDO {
    @TableId
    private Long id;
    private String connectionId;
    private String clientId;
    private String externalAccountId;
    private String displayName;
    private String username;
    private String accessTokenCipher;
    private String refreshTokenCipher;
    private LocalDateTime tokenExpiresAt;
    private String scopes;
    private String authStatus;
    private String endUserId;
    private Long sourceSocialSessionId;
    private Long sourceSocialAccountId;
    private String failReason;
    private LocalDateTime lastAuthTime;
}
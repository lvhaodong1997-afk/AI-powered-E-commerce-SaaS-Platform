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
@TableName("tk_open_tiktok_connection")
@KeySequence("tk_open_tiktok_connection_seq")
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TkOpenTiktokConnectionDO extends BaseDO {
    @TableId
    private Long id;
    private String connectionId;
    private String clientId;
    private String externalAccountId;
    private String openId;
    private String displayName;
    private String username;
    private String avatarUrl;
    private String scopes;
    private String accessTokenCipher;
    private String refreshTokenCipher;
    private LocalDateTime accessTokenExpireTime;
    private LocalDateTime refreshTokenExpireTime;
    private String tokenStatus;
    private String authStatus;
    private LocalDateTime lastAuthTime;
    private LocalDateTime lastPublishTime;
    private String failReason;
}

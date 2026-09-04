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
@TableName("tk_open_tiktok_auth_session")
@KeySequence("tk_open_tiktok_auth_session_seq")
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TkOpenTiktokAuthSessionDO extends BaseDO {
    @TableId
    private Long id;
    private String authSessionId;
    private String clientId;
    private String externalAccountId;
    private String clientState;
    private String authMode;
    private String oauthState;
    private String clientTicket;
    private String qrcodeToken;
    private String qrcodeUrl;
    private String authorizeUrl;
    private String status;
    private String connectionId;
    private String accountName;
    private String failReason;
    private LocalDateTime expireTime;
}

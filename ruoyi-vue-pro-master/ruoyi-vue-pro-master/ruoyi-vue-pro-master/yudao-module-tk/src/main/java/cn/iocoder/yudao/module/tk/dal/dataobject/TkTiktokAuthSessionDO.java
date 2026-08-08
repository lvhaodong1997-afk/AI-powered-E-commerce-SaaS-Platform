package cn.iocoder.yudao.module.tk.dal.dataobject;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

@TableName("tk_tiktok_auth_session")
@KeySequence("tk_tiktok_auth_session_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TkTiktokAuthSessionDO extends TenantBaseDO {

    @TableId
    private Long id;

    private Long companyId;
    private Long userId;
    private String authType;
    private String state;
    private String codeVerifier;
    private String codeChallenge;
    private String clientTicket;
    private String qrcodeToken;
    private String qrcodeUrl;
    private String authorizeUrl;
    private String status;
    private String failReason;
    private LocalDateTime expireTime;

}

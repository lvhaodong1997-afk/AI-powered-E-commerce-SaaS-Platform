package cn.iocoder.yudao.module.tk.dal.dataobject;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

@TableName("tk_tiktok_account")
@KeySequence("tk_tiktok_account_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TkTiktokAccountDO extends TenantBaseDO {

    @TableId
    private Long id;

    private Long companyId;
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
    private String defaultPrivacyLevel;
    private Boolean allowComment;
    private Boolean allowDuet;
    private Boolean allowStitch;
    private Boolean commercialContent;
    private Boolean brandContent;
    private Boolean aigcContent;
    private String labels;
    private LocalDateTime lastAuthTime;
    private LocalDateTime lastPublishTime;
    private String failReason;
    private Integer status;

}

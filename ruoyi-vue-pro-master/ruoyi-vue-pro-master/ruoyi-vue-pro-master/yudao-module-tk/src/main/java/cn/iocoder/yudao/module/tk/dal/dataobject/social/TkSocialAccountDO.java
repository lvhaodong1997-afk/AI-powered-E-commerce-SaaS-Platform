package cn.iocoder.yudao.module.tk.dal.dataobject.social;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.*;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper=true)
@TableName("tk_social_account")
public class TkSocialAccountDO extends TenantBaseDO {
    @TableId private Long id;
    private Long companyId;
    private String platform,accountType,externalAccountId,providerUserId,accountName,username;
    @ToString.Exclude
    @TableField(updateStrategy=FieldStrategy.ALWAYS)
    private String accessTokenCiphertext;
    private String tokenType,scopes,status;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String failReason;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private LocalDateTime tokenExpiresAt;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private LocalDateTime lastValidatedAt;
    private LocalDateTime lastAuthTime;
}

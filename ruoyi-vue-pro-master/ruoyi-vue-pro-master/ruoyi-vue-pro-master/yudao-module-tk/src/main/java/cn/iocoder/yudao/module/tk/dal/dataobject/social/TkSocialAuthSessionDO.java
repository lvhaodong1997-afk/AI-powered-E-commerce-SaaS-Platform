package cn.iocoder.yudao.module.tk.dal.dataobject.social;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.*;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper=true)
@TableName("tk_social_auth_session")
public class TkSocialAuthSessionDO extends TenantBaseDO {
    @TableId private Long id;
    private Long companyId;
    private String sessionId,platform;
    @ToString.Exclude private String stateHash;
    private String status;
    @ToString.Exclude
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String payloadCiphertext;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String failReason;
    private LocalDateTime expireTime;
}

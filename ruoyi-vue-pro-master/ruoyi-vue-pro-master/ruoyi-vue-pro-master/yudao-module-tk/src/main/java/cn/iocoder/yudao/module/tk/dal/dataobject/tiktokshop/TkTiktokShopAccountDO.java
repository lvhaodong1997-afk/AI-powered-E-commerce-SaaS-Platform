package cn.iocoder.yudao.module.tk.dal.dataobject.tiktokshop;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import java.time.LocalDateTime;

@TableName("tk_tiktok_shop_account")
@KeySequence("tk_tiktok_shop_account_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class TkTiktokShopAccountDO extends TenantBaseDO {
    @TableId
    private Long id;
    private Long companyId;
    private String appKey;
    private String openId;
    private String displayName;
    private String username;
    private String market;
    private Integer userType;
    private String grantedScopes;
    @ToString.Exclude
    private String accessTokenCipher;
    @ToString.Exclude
    private String refreshTokenCipher;
    private LocalDateTime accessTokenExpireTime;
    private LocalDateTime refreshTokenExpireTime;
    private String authStatus;
    private Long tokenVersion;
}
package cn.iocoder.yudao.module.tk.dal.dataobject.tiktokshop;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import java.time.LocalDateTime;

@TableName("tk_tiktok_shop_auth_session")
@KeySequence("tk_tiktok_shop_auth_session_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class TkTiktokShopAuthSessionDO extends TenantBaseDO {
    @TableId
    private Long id;
    private Long companyId;
    private Long userId;
    private String stateHash;
    private String status;
    private LocalDateTime expireTime;
    private LocalDateTime consumedTime;
    private String failReason;
}
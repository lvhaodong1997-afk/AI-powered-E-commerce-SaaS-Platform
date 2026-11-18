package cn.iocoder.yudao.module.tk.dal.dataobject.tiktokshop;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tk_tiktok_shop_photo")
public class TkTiktokShopPhotoDO extends TenantBaseDO {
    @TableId private Long id;
    private Long companyId, accountId;
    private String idempotencyKey, requestHash, title, productId, stage, photoId, postStatus, failCode, requestId;
}

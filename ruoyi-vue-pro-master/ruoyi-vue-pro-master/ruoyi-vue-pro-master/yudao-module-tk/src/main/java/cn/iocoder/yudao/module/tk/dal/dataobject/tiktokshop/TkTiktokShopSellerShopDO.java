package cn.iocoder.yudao.module.tk.dal.dataobject.tiktokshop;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.*;
@TableName("tk_tiktok_shop_seller_shop")
@Data @EqualsAndHashCode(callSuper=true) @ToString(callSuper=true)
public class TkTiktokShopSellerShopDO extends TenantBaseDO {
    @TableId private Long id;
    private Long accountId,companyId;
    private String shopId,name,region,status,environment,sellerType,code;
    @ToString.Exclude private String shopCipher;
}

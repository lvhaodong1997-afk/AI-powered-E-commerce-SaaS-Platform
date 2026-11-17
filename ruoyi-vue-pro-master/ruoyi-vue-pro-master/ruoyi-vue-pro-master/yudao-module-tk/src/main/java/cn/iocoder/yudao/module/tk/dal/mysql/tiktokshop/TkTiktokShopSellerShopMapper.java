package cn.iocoder.yudao.module.tk.dal.mysql.tiktokshop;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.tk.dal.dataobject.tiktokshop.TkTiktokShopSellerShopDO;
import org.apache.ibatis.annotations.*;
import java.util.List;
@Mapper
public interface TkTiktokShopSellerShopMapper extends BaseMapperX<TkTiktokShopSellerShopDO> {
    @Select("SELECT * FROM tk_tiktok_shop_seller_shop WHERE account_id=#{account} AND tenant_id=#{tenant} AND deleted=0")
    List<TkTiktokShopSellerShopDO> findByAccount(@Param("tenant") Long tenant,@Param("account") Long account);
    @Update("UPDATE tk_tiktok_shop_seller_shop SET status='UNAVAILABLE', shop_cipher=NULL WHERE account_id=#{account} AND tenant_id=#{tenant} AND deleted=0")
    int invalidate(@Param("tenant") Long tenant,@Param("account") Long account);
}

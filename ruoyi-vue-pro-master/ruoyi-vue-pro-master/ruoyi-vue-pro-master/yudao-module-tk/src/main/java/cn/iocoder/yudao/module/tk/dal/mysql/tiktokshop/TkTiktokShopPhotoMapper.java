package cn.iocoder.yudao.module.tk.dal.mysql.tiktokshop;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.tk.dal.dataobject.tiktokshop.TkTiktokShopPhotoDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface TkTiktokShopPhotoMapper extends BaseMapperX<TkTiktokShopPhotoDO> {
    @Update("UPDATE tk_tiktok_shop_photo SET stage=#{to}, photo_id=#{a.photoId}, fail_code=#{a.failCode}, request_id=#{a.requestId}, update_time=CURRENT_TIMESTAMP "
            + "WHERE id=#{a.id} AND tenant_id=#{a.tenantId} AND stage=#{from} AND deleted=0")
    int transition(@Param("a") TkTiktokShopPhotoDO row,@Param("from") String from,@Param("to") String to);
}

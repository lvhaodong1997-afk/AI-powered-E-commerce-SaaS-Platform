package cn.iocoder.yudao.module.tk.dal.mysql.social;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.tk.dal.dataobject.social.TkSocialMediaDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TkSocialMediaMapper extends BaseMapperX<TkSocialMediaDO> {
    @Select("SELECT * FROM tk_social_media WHERE id=#{id} AND tenant_id=#{tenantId} AND deleted=0 FOR UPDATE")
    TkSocialMediaDO lockMedia(@Param("id") Long id,@Param("tenantId") Long tenantId);
}

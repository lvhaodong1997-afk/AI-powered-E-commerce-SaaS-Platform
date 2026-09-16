package cn.iocoder.yudao.module.tk.dal.mysql.social;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.tk.dal.dataobject.social.TkSocialPublishTaskDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TkSocialPublishTaskMapper extends BaseMapperX<TkSocialPublishTaskDO> {
    @org.apache.ibatis.annotations.Select("SELECT * FROM tk_social_publish_task WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = 0 FOR UPDATE")
    TkSocialPublishTaskDO lockTask(@org.apache.ibatis.annotations.Param("id") Long id,
            @org.apache.ibatis.annotations.Param("tenantId") Long tenantId);
}

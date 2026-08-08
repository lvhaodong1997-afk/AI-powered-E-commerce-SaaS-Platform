package cn.iocoder.yudao.module.tk.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkVoiceProfileDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface TkVoiceProfileMapper extends BaseMapperX<TkVoiceProfileDO> {

    default List<TkVoiceProfileDO> selectListByTenant(Long tenantId) {
        return selectList(new LambdaQueryWrapperX<TkVoiceProfileDO>()
                .eq(TkVoiceProfileDO::getTenantId, tenantId)
                .orderByDesc(TkVoiceProfileDO::getId));
    }

}

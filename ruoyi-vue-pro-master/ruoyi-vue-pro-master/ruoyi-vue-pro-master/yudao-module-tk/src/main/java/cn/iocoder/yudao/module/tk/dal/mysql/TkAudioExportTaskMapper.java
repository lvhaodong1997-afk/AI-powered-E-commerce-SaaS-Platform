package cn.iocoder.yudao.module.tk.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkAudioExportTaskDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TkAudioExportTaskMapper extends BaseMapperX<TkAudioExportTaskDO> {

    default TkAudioExportTaskDO selectByRequestId(Long tenantId, String requestId) {
        return selectOne(new LambdaQueryWrapperX<TkAudioExportTaskDO>()
                .eq(TkAudioExportTaskDO::getTenantId, tenantId)
                .eq(TkAudioExportTaskDO::getRequestId, requestId));
    }
}

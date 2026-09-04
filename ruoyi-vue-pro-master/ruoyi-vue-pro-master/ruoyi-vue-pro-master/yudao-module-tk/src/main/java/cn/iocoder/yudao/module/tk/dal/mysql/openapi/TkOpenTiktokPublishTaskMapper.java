package cn.iocoder.yudao.module.tk.dal.mysql.openapi;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenTiktokPublishTaskDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface TkOpenTiktokPublishTaskMapper extends BaseMapperX<TkOpenTiktokPublishTaskDO> {
    default TkOpenTiktokPublishTaskDO selectByClientAndTaskId(String clientId, String taskId) {
        return selectOne(new LambdaQueryWrapperX<TkOpenTiktokPublishTaskDO>()
                .eq(TkOpenTiktokPublishTaskDO::getClientId, clientId)
                .eq(TkOpenTiktokPublishTaskDO::getTaskId, taskId));
    }

    default TkOpenTiktokPublishTaskDO selectByClientAndTaskIdForUpdate(String clientId, String taskId) {
        return selectOne(new LambdaQueryWrapperX<TkOpenTiktokPublishTaskDO>()
                .eq(TkOpenTiktokPublishTaskDO::getClientId, clientId)
                .eq(TkOpenTiktokPublishTaskDO::getTaskId, taskId)
                .last("FOR UPDATE"));
    }

    default List<TkOpenTiktokPublishTaskDO> selectListByClient(String clientId, int limit) {
        return selectList(new LambdaQueryWrapperX<TkOpenTiktokPublishTaskDO>()
                .eq(TkOpenTiktokPublishTaskDO::getClientId, clientId)
                .orderByDesc(TkOpenTiktokPublishTaskDO::getId)
                .last("LIMIT " + Math.max(1, Math.min(limit, 200))));
    }
}

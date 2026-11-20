package cn.iocoder.yudao.module.tk.dal.mysql.openapi;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenXPublishTaskDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface TkOpenXPublishTaskMapper extends BaseMapperX<TkOpenXPublishTaskDO> {
    default TkOpenXPublishTaskDO selectByClientAndTaskId(String clientId, String taskId) {
        return selectOne(new LambdaQueryWrapperX<TkOpenXPublishTaskDO>()
                .eq(TkOpenXPublishTaskDO::getClientId, clientId)
                .eq(TkOpenXPublishTaskDO::getTaskId, taskId));
    }

    default TkOpenXPublishTaskDO selectByClientAndIdempotencyKey(String clientId, String idempotencyKey) {
        return selectOne(new LambdaQueryWrapperX<TkOpenXPublishTaskDO>()
                .eq(TkOpenXPublishTaskDO::getClientId, clientId)
                .eq(TkOpenXPublishTaskDO::getIdempotencyKey, idempotencyKey));
    }

    default List<TkOpenXPublishTaskDO> selectDue(LocalDateTime now, int limit) {
        int safe = Math.max(1, Math.min(limit, 32));
        return selectList(new LambdaQueryWrapperX<TkOpenXPublishTaskDO>()
                .in(TkOpenXPublishTaskDO::getStatus, "PENDING", "PROCESSING")
                .and(wrapper -> wrapper.isNull(TkOpenXPublishTaskDO::getNextRetryTime)
                        .or().le(TkOpenXPublishTaskDO::getNextRetryTime, now))
                .and(wrapper -> wrapper.isNull(TkOpenXPublishTaskDO::getLeaseUntil)
                        .or().le(TkOpenXPublishTaskDO::getLeaseUntil, now))
                .orderByAsc(TkOpenXPublishTaskDO::getId)
                .last("LIMIT " + safe));
    }

    default TkOpenXPublishTaskDO selectByIdForUpdate(Long id) {
        return selectOne(new LambdaQueryWrapperX<TkOpenXPublishTaskDO>()
                .eq(TkOpenXPublishTaskDO::getId, id)
                .last("FOR UPDATE"));
    }
}
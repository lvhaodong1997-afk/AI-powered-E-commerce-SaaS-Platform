package cn.iocoder.yudao.module.tk.dal.mysql.openapi;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenTiktokPublishDetailDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface TkOpenTiktokPublishDetailMapper extends BaseMapperX<TkOpenTiktokPublishDetailDO> {
    default TkOpenTiktokPublishDetailDO selectByClientAndDetailId(String clientId, String detailId) {
        return selectOne(new LambdaQueryWrapperX<TkOpenTiktokPublishDetailDO>()
                .eq(TkOpenTiktokPublishDetailDO::getClientId, clientId)
                .eq(TkOpenTiktokPublishDetailDO::getDetailId, detailId));
    }

    default List<TkOpenTiktokPublishDetailDO> selectListByClientAndTaskId(String clientId, String taskId) {
        return selectList(new LambdaQueryWrapperX<TkOpenTiktokPublishDetailDO>()
                .eq(TkOpenTiktokPublishDetailDO::getClientId, clientId)
                .eq(TkOpenTiktokPublishDetailDO::getTaskId, taskId)
                .orderByAsc(TkOpenTiktokPublishDetailDO::getId));
    }

    default List<TkOpenTiktokPublishDetailDO> selectStalePending(LocalDateTime deadline, int limit) {
        return selectList(new LambdaQueryWrapperX<TkOpenTiktokPublishDetailDO>()
                .eq(TkOpenTiktokPublishDetailDO::getStatus, "PENDING")
                .le(TkOpenTiktokPublishDetailDO::getUpdateTime, deadline)
                .orderByAsc(TkOpenTiktokPublishDetailDO::getId)
                .last("LIMIT " + Math.max(1, Math.min(limit, 200))));
    }

    default List<TkOpenTiktokPublishDetailDO> selectStaleInitializing(LocalDateTime deadline, int limit) {
        return selectList(new LambdaQueryWrapperX<TkOpenTiktokPublishDetailDO>()
                .eq(TkOpenTiktokPublishDetailDO::getStatus, "PROCESSING")
                .isNull(TkOpenTiktokPublishDetailDO::getPublishId)
                .and(wrapper -> wrapper.isNull(TkOpenTiktokPublishDetailDO::getLastSyncTime)
                        .or().le(TkOpenTiktokPublishDetailDO::getLastSyncTime, deadline))
                .orderByAsc(TkOpenTiktokPublishDetailDO::getId)
                .last("LIMIT " + Math.max(1, Math.min(limit, 200))));
    }

    default List<TkOpenTiktokPublishDetailDO> selectStaleProcessing(LocalDateTime deadline, int limit) {
        return selectList(new LambdaQueryWrapperX<TkOpenTiktokPublishDetailDO>()
                .eq(TkOpenTiktokPublishDetailDO::getStatus, "PROCESSING")
                .isNotNull(TkOpenTiktokPublishDetailDO::getPublishId)
                .and(wrapper -> wrapper.isNull(TkOpenTiktokPublishDetailDO::getLastSyncTime)
                        .or().le(TkOpenTiktokPublishDetailDO::getLastSyncTime, deadline))
                .orderByAsc(TkOpenTiktokPublishDetailDO::getLastSyncTime)
                .last("LIMIT " + Math.max(1, Math.min(limit, 200))));
    }
}

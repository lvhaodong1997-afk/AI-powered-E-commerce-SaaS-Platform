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

    default List<TkOpenTiktokPublishDetailDO> selectListByClientAndTaskIdForUpdate(String clientId, String taskId) {
        return selectList(new LambdaQueryWrapperX<TkOpenTiktokPublishDetailDO>()
                .eq(TkOpenTiktokPublishDetailDO::getClientId, clientId)
                .eq(TkOpenTiktokPublishDetailDO::getTaskId, taskId)
                .orderByAsc(TkOpenTiktokPublishDetailDO::getId)
                .last("FOR UPDATE"));
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
                .and(wrapper -> wrapper.isNull(TkOpenTiktokPublishDetailDO::getTiktokStatus)
                        .or().ne(TkOpenTiktokPublishDetailDO::getTiktokStatus, "RECOVERY_REQUIRED"))
                .and(wrapper -> wrapper.isNull(TkOpenTiktokPublishDetailDO::getLastSyncTime)
                        .or().le(TkOpenTiktokPublishDetailDO::getLastSyncTime, deadline))
                .orderByAsc(TkOpenTiktokPublishDetailDO::getId)
                .last("LIMIT " + Math.max(1, Math.min(limit, 200))));
    }

    default List<TkOpenTiktokPublishDetailDO> selectRecoveryRequired(int limit) {
        return selectList(new LambdaQueryWrapperX<TkOpenTiktokPublishDetailDO>()
                .eq(TkOpenTiktokPublishDetailDO::getStatus, "PROCESSING")
                .isNull(TkOpenTiktokPublishDetailDO::getPublishId)
                .eq(TkOpenTiktokPublishDetailDO::getTiktokStatus, "RECOVERY_REQUIRED")
                .orderByAsc(TkOpenTiktokPublishDetailDO::getLastSyncTime)
                .last("LIMIT " + Math.max(1, Math.min(limit, 200))));
    }

    default List<TkOpenTiktokPublishDetailDO> selectTerminalForCallback(int limit) {
        return selectList(new LambdaQueryWrapperX<TkOpenTiktokPublishDetailDO>()
                .in(TkOpenTiktokPublishDetailDO::getStatus, java.util.Arrays.asList("SUCCESS", "FAILED"))
                .apply("(fail_reason IS NULL OR fail_reason <> 'TikTok publish was not found after reconciliation')")
                .apply("(status <> 'SUCCESS' OR (publish_id IS NOT NULL AND publish_id <> ''))")
                .apply("(NOT EXISTS (SELECT 1 FROM tk_open_api_event e"
                        + " WHERE e.client_id = tk_open_tiktok_publish_detail.client_id"
                        + " AND e.dedupe_key = CONCAT(tk_open_tiktok_publish_detail.client_id, '|publish.',"
                        + " LOWER(tk_open_tiktok_publish_detail.status), '|PUBLISH_DETAIL|',"
                        + " tk_open_tiktok_publish_detail.detail_id, '|', COALESCE(tk_open_tiktok_publish_detail.retry_count, 0))"
                        + " AND e.deleted = 0)"
                        + " OR EXISTS (SELECT 1 FROM tk_open_tiktok_publish_task t"
                        + " WHERE t.client_id = tk_open_tiktok_publish_detail.client_id"
                        + " AND t.task_id = tk_open_tiktok_publish_detail.task_id AND t.deleted = 0"
                        + " AND t.status IN ('PENDING', 'PROCESSING')"
                        + " AND NOT EXISTS (SELECT 1 FROM tk_open_tiktok_publish_detail pending"
                        + " WHERE pending.client_id = t.client_id AND pending.task_id = t.task_id"
                        + " AND pending.deleted = 0 AND pending.status NOT IN ('SUCCESS', 'FAILED'))))")
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

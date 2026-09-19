package cn.iocoder.yudao.module.tk.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkTiktokWebhookEventDO;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface TkTiktokWebhookEventMapper extends BaseMapperX<TkTiktokWebhookEventDO> {

    default TkTiktokWebhookEventDO selectByEventId(String eventId) {
        return selectOne(TkTiktokWebhookEventDO::getEventId, eventId);
    }

    default List<TkTiktokWebhookEventDO> selectRetryBatch(LocalDateTime retryBefore, int limit) {
        return selectList(new QueryWrapper<TkTiktokWebhookEventDO>()
                .in("status", "RECEIVED", "RETRYING", "FAILED")
                .and(q -> q.isNull("processed_time").or().le("processed_time", retryBefore))
                // Rotate attempted unmatched/failed rows behind older work, including fresh RECEIVED rows.
                .orderByAsc("COALESCE(processed_time, received_time)", "id")
                .last("LIMIT " + Math.max(1, Math.min(limit, 100))));
    }

    default int claimForProcessing(Long id, LocalDateTime claimedAt, LocalDateTime retryBefore) {
        return update(null, new LambdaUpdateWrapper<TkTiktokWebhookEventDO>()
                .eq(TkTiktokWebhookEventDO::getId, id)
                .in(TkTiktokWebhookEventDO::getStatus, "RECEIVED", "RETRYING", "FAILED")
                .and(q -> q.isNull(TkTiktokWebhookEventDO::getProcessedTime)
                        .or().le(TkTiktokWebhookEventDO::getProcessedTime, retryBefore))
                .set(TkTiktokWebhookEventDO::getStatus, "RETRYING")
                .set(TkTiktokWebhookEventDO::getProcessedTime, claimedAt));
    }

    default int finishAttempt(Long id, LocalDateTime claimedAt, String status, String reason,
                              LocalDateTime finishedAt) {
        return update(null, new LambdaUpdateWrapper<TkTiktokWebhookEventDO>()
                .eq(TkTiktokWebhookEventDO::getId, id)
                .eq(TkTiktokWebhookEventDO::getStatus, "RETRYING")
                .eq(TkTiktokWebhookEventDO::getProcessedTime, claimedAt)
                .set(TkTiktokWebhookEventDO::getStatus, status)
                // Explicit SET also clears old errors, unlike updateById's non-null field strategy.
                .set(TkTiktokWebhookEventDO::getFailReason, reason)
                .set(TkTiktokWebhookEventDO::getProcessedTime, finishedAt));
    }

}

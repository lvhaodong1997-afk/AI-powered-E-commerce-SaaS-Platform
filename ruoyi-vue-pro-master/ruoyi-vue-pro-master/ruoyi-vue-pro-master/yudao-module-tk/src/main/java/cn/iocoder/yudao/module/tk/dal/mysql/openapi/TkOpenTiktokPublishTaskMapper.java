package cn.iocoder.yudao.module.tk.dal.mysql.openapi;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenTiktokPublishTaskDO;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.time.LocalDateTime;

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

    @InterceptorIgnore(tenantLine = "true", dataPermission = "true")
    @Select("SELECT * FROM tk_open_tiktok_publish_task WHERE deleted = b'0' "
            + "AND status = 'SCHEDULED' "
            + "AND ((schedule_utc_time IS NOT NULL AND schedule_utc_time <= #{utcNow}) "
            + "OR (schedule_utc_time IS NULL AND scheduled_at IS NOT NULL AND scheduled_at <= #{legacyNow})) "
            + "ORDER BY COALESCE(schedule_utc_time, scheduled_at) ASC, id ASC LIMIT #{limit}")
    List<TkOpenTiktokPublishTaskDO> selectDueScheduled(@Param("utcNow") LocalDateTime utcNow,
                                                       @Param("legacyNow") LocalDateTime legacyNow,
                                                       @Param("limit") int limit);
}

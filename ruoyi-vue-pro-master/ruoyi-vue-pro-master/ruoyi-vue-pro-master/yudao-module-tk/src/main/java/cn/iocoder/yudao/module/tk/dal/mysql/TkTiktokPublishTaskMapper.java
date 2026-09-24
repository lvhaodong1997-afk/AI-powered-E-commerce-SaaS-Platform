package cn.iocoder.yudao.module.tk.dal.mysql;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo.TkTiktokPublishTaskPageReqVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkTiktokPublishTaskDO;
import cn.iocoder.yudao.module.tk.service.scope.TkUserScope;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface TkTiktokPublishTaskMapper extends BaseMapperX<TkTiktokPublishTaskDO> {

    default PageResult<TkTiktokPublishTaskDO> selectPage(TkTiktokPublishTaskPageReqVO reqVO, TkUserScope scope) {
        return selectPage(reqVO, new LambdaQueryWrapperX<TkTiktokPublishTaskDO>()
                .eqIfPresent(TkTiktokPublishTaskDO::getTenantId, scope.isGlobalPlatformView() ? null : scope.getTenantId())
                .eqIfPresent(TkTiktokPublishTaskDO::getCompanyId, scope.isPlatformAdmin() ? reqVO.getCompanyId() : null)
                .eqIfPresent(TkTiktokPublishTaskDO::getCreator, scope.canReadAllTenantRecords() ? null : scope.getUserIdString())
                .eqIfPresent(TkTiktokPublishTaskDO::getGenerationTaskId, reqVO.getGenerationTaskId())
                .eqIfPresent(TkTiktokPublishTaskDO::getBusinessTraceId, reqVO.getBusinessTraceId())
                .eqIfPresent(TkTiktokPublishTaskDO::getStatus, reqVO.getStatus())
                .and(StrUtil.isNotBlank(reqVO.getKeyword()), wrapper -> wrapper
                        .like(TkTiktokPublishTaskDO::getTitle, reqVO.getKeyword())
                        .or()
                        .like(TkTiktokPublishTaskDO::getCaption, reqVO.getKeyword()))
                .orderByDesc(TkTiktokPublishTaskDO::getId));
    }

    default Long selectPendingCount(TkUserScope scope) {
        return selectCount(new LambdaQueryWrapperX<TkTiktokPublishTaskDO>()
                .eqIfPresent(TkTiktokPublishTaskDO::getTenantId, scope.isGlobalPlatformView() ? null : scope.getTenantId())
                .eqIfPresent(TkTiktokPublishTaskDO::getCreator, scope.canReadAllTenantRecords() ? null : scope.getUserIdString())
                .in(TkTiktokPublishTaskDO::getStatus, java.util.Arrays.asList("PENDING", "PROCESSING", "PARTIAL_SUCCESS")));
    }

    default Long selectFailedCount(TkUserScope scope) {
        return selectCount(new LambdaQueryWrapperX<TkTiktokPublishTaskDO>()
                .eqIfPresent(TkTiktokPublishTaskDO::getTenantId, scope.isGlobalPlatformView() ? null : scope.getTenantId())
                .eqIfPresent(TkTiktokPublishTaskDO::getCreator, scope.canReadAllTenantRecords() ? null : scope.getUserIdString())
                .in(TkTiktokPublishTaskDO::getStatus, java.util.Arrays.asList("FAILED", "PARTIAL_SUCCESS")));
    }

    @Select("SELECT * FROM tk_tiktok_publish_task WHERE deleted = b'0' "
            + "AND status = 'SCHEDULED' AND schedule_status = 'SCHEDULED' "
            + "AND scheduled_at IS NOT NULL AND scheduled_at &lt;= #{now} "
            + "ORDER BY scheduled_at ASC, id ASC LIMIT #{limit}")
    List<TkTiktokPublishTaskDO> selectDueScheduled(@Param("now") LocalDateTime now,
                                                   @Param("limit") int limit);

    @Update("UPDATE tk_tiktok_publish_task SET status = 'PENDING', schedule_status = 'RUNNING', "
            + "schedule_version = schedule_version + 1, started_at = #{now}, updater = 'tk-scheduler', "
            + "update_time = NOW() WHERE id = #{id} AND deleted = b'0' AND status = 'SCHEDULED' "
            + "AND schedule_status = 'SCHEDULED' AND schedule_version = #{version} "
            + "AND scheduled_at &lt;= #{now}")
    int claimScheduled(@Param("id") Long id, @Param("version") Integer version,
                       @Param("now") LocalDateTime now);

    @Update("UPDATE tk_tiktok_publish_task SET scheduled_at = #{scheduledAt}, "
            + "schedule_version = schedule_version + 1, updater = 'tk-scheduler', update_time = NOW() "
            + "WHERE id = #{id} AND deleted = b'0' AND status = 'SCHEDULED' "
            + "AND schedule_status = 'SCHEDULED' AND schedule_version = #{version}")
    int rescheduleScheduled(@Param("id") Long id, @Param("version") Integer version,
                            @Param("scheduledAt") LocalDateTime scheduledAt);

    @Update("UPDATE tk_tiktok_publish_task SET status = 'CANCELLED', schedule_status = 'CANCELLED', "
            + "schedule_version = schedule_version + 1, pending_count = 0, finished_at = #{now}, "
            + "updater = 'tk-scheduler', update_time = NOW() WHERE id = #{id} AND deleted = b'0' "
            + "AND status = 'SCHEDULED' AND schedule_status = 'SCHEDULED' AND schedule_version = #{version}")
    int cancelScheduled(@Param("id") Long id, @Param("version") Integer version,
                        @Param("now") LocalDateTime now);

}


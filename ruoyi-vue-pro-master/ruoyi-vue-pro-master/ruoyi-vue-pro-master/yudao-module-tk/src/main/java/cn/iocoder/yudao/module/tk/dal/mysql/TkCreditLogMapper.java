package cn.iocoder.yudao.module.tk.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.tk.controller.admin.dashboard.vo.TkDashboardCountItemRespVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkCreditLogDO;
import cn.iocoder.yudao.module.tk.service.scope.TkUserScope;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface TkCreditLogMapper extends BaseMapperX<TkCreditLogDO> {

    default TkCreditLogDO selectInProgress(String bizType, Long bizId) {
        return selectOne(new LambdaQueryWrapperX<TkCreditLogDO>()
                .eq(TkCreditLogDO::getBizType, bizType)
                .eq(TkCreditLogDO::getBizId, bizId)
                .eq(TkCreditLogDO::getStatus, "IN_PROGRESS"));
    }

    default TkCreditLogDO selectInProgressById(Long id) {
        return selectOne(new LambdaQueryWrapperX<TkCreditLogDO>()
                .eq(TkCreditLogDO::getId, id)
                .eq(TkCreditLogDO::getStatus, "IN_PROGRESS"));
    }

    default List<TkCreditLogDO> selectInProgressBefore(LocalDateTime deadline, int limit) {
        return selectList(new LambdaQueryWrapperX<TkCreditLogDO>()
                .eq(TkCreditLogDO::getStatus, "IN_PROGRESS")
                .isNotNull(TkCreditLogDO::getBizId)
                .le(TkCreditLogDO::getCreateTime, deadline)
                .orderByAsc(TkCreditLogDO::getId)
                .last("LIMIT " + limit));
    }

    default Long selectSettledCredits(TkUserScope scope, LocalDateTime startTime, LocalDateTime endTime) {
        return selectSettledCredits(scope.isGlobalPlatformView() ? null : scope.getTenantId(),
                scope.canReadAllTenantRecords() ? null : scope.getUserIdString(), startTime, endTime);
    }

    @Select("<script>"
            + "SELECT COALESCE(SUM(credits), 0) FROM tk_credit_log "
            + "WHERE deleted = 0 AND status = 'SETTLED' "
            + "AND action IN ('FREEZE', 'SETTLE') "
            + "AND biz_type IN ('REFERENCE_ANALYSIS', 'GENERATION_TASK', 'AUDIO_EXPORT') "
            + "<if test='tenantId != null'> AND tenant_id = #{tenantId} </if>"
            + "<if test='creator != null and creator != \"\"'> AND creator = #{creator} </if>"
            + "<if test='startTime != null'> AND create_time &gt;= #{startTime} </if>"
            + "<if test='endTime != null'> AND create_time &lt; #{endTime} </if>"
            + "</script>")
    Long selectSettledCredits(@Param("tenantId") Long tenantId,
                              @Param("creator") String creator,
                              @Param("startTime") LocalDateTime startTime,
                              @Param("endTime") LocalDateTime endTime);

    default List<TkDashboardCountItemRespVO> selectDailySettledCredits(TkUserScope scope,
                                                                       LocalDateTime startTime,
                                                                       LocalDateTime endTime) {
        return selectDailySettledCredits(scope.isGlobalPlatformView() ? null : scope.getTenantId(),
                scope.canReadAllTenantRecords() ? null : scope.getUserIdString(), startTime, endTime);
    }

    @Select("<script>"
            + "SELECT DATE(create_time) AS name, COALESCE(SUM(credits), 0) AS value FROM tk_credit_log "
            + "WHERE deleted = 0 AND status = 'SETTLED' "
            + "AND action IN ('FREEZE', 'SETTLE') "
            + "AND biz_type IN ('REFERENCE_ANALYSIS', 'GENERATION_TASK', 'AUDIO_EXPORT') "
            + "<if test='tenantId != null'> AND tenant_id = #{tenantId} </if>"
            + "<if test='creator != null and creator != \"\"'> AND creator = #{creator} </if>"
            + "<if test='startTime != null'> AND create_time &gt;= #{startTime} </if>"
            + "<if test='endTime != null'> AND create_time &lt; #{endTime} </if>"
            + "GROUP BY DATE(create_time) ORDER BY name"
            + "</script>")
    List<TkDashboardCountItemRespVO> selectDailySettledCredits(@Param("tenantId") Long tenantId,
                                                               @Param("creator") String creator,
                                                               @Param("startTime") LocalDateTime startTime,
                                                               @Param("endTime") LocalDateTime endTime);

}

package cn.iocoder.yudao.module.tk.dal.mysql;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.tk.controller.admin.dashboard.vo.TkDashboardCountItemRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.dashboard.vo.TkDashboardSlowTaskRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.dashboard.vo.TkDashboardTrendRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.generation.route.vo.TkGenerationRouteStatisticsRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.generation.vo.TkGenerationTaskPageReqVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationTaskDO;
import cn.iocoder.yudao.module.tk.enums.TkGenerationStatusEnum;
import cn.iocoder.yudao.module.tk.service.scope.TkUserScope;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Mapper
public interface TkGenerationTaskMapper extends BaseMapperX<TkGenerationTaskDO> {

    @Update("<script>UPDATE tk_generation_task SET lease_token = #{leaseToken}, worker_id = #{workerId}, "
            + "heartbeat_time = NOW(), lease_expire_time = #{leaseExpireTime}, update_time = NOW() "
            + "WHERE id = #{id} AND deleted = 0 AND ((status = 'PENDING' "
            + "AND (lease_token IS NULL OR lease_expire_time IS NULL OR lease_expire_time &lt; NOW())) "
            + "OR (status IN ('PRECHECKED','ANALYZING','SCRIPT_READY','VOICE_READY','MATERIAL_MATCHING',"
            + "'MATERIAL_MATCHED','SUBTITLE_TIMELINE_READY','VISUAL_ANALYZED','CLIP_PLANNED','RENDERING','EXPORTING') "
            + "AND (heartbeat_time IS NULL OR heartbeat_time &lt; #{staleBefore})))</script>")
    int claimTask(@Param("id") Long id, @Param("leaseToken") String leaseToken,
                  @Param("workerId") String workerId, @Param("staleBefore") LocalDateTime staleBefore,
                  @Param("leaseExpireTime") LocalDateTime leaseExpireTime);

    @Update("UPDATE tk_generation_task SET heartbeat_time = NOW(), lease_expire_time = #{leaseExpireTime}, update_time = NOW() "
            + "WHERE id = #{id} AND deleted = 0 AND lease_token = #{leaseToken}")
    int renewTaskLease(@Param("id") Long id, @Param("leaseToken") String leaseToken,
                       @Param("leaseExpireTime") LocalDateTime leaseExpireTime);

    @Update("UPDATE tk_generation_task SET lease_token = NULL, lease_expire_time = NULL, update_time = NOW() "
            + "WHERE id = #{id} AND deleted = 0 AND lease_token = #{leaseToken}")
    int releaseTaskLease(@Param("id") Long id, @Param("leaseToken") String leaseToken);

    @Update("<script>UPDATE tk_generation_task SET status = 'PENDING', progress = 0, fail_reason = NULL, "
            + "fail_code = NULL, current_step = 'RETRY_PENDING', retry_count = #{retryCount}, "
            + "last_retry_time = #{lastRetryTime}, worker_id = NULL, heartbeat_time = NULL, "
            + "lease_token = NULL, lease_expire_time = NULL, step_started_at = NULL, step_finished_at = NULL, "
            + "output_url = NULL, subtitle_url = NULL, subtitle_timeline_url = NULL, "
            + "subtitle_visual_analysis_url = NULL, subtitle_layout_url = NULL, subtitle_ass_url = NULL "
            + "<if test='clearAudio'>, audio_url = NULL, clip_plan = NULL</if>, update_time = NOW() "
            + "WHERE id = #{id} AND deleted = 0</script>")
    int resetForRetry(@Param("id") Long id, @Param("retryCount") Integer retryCount,
                      @Param("lastRetryTime") LocalDateTime lastRetryTime, @Param("clearAudio") boolean clearAudio);

    default List<TkGenerationTaskDO> selectExpiredTasksWithGenerationUrls(LocalDateTime deadline, int limit) {
        LambdaQueryWrapperX<TkGenerationTaskDO> wrapper = new LambdaQueryWrapperX<>();
        wrapper.select(TkGenerationTaskDO::getId,
                TkGenerationTaskDO::getStatus,
                TkGenerationTaskDO::getOutputUrl,
                TkGenerationTaskDO::getAudioUrl,
                TkGenerationTaskDO::getSubtitleUrl,
                TkGenerationTaskDO::getSubtitleTimelineUrl,
                TkGenerationTaskDO::getSubtitleVisualAnalysisUrl,
                TkGenerationTaskDO::getSubtitleLayoutUrl,
                TkGenerationTaskDO::getSubtitleAssUrl,
                TkGenerationTaskDO::getCreateTime);
        return selectList(wrapper
                .lt(TkGenerationTaskDO::getCreateTime, deadline)
                .in(TkGenerationTaskDO::getStatus, TkGenerationStatusEnum.SUCCESS, TkGenerationStatusEnum.FAILED)
                .and(item -> item
                        .isNotNull(TkGenerationTaskDO::getOutputUrl).ne(TkGenerationTaskDO::getOutputUrl, "")
                        .or().isNotNull(TkGenerationTaskDO::getAudioUrl).ne(TkGenerationTaskDO::getAudioUrl, "")
                        .or().isNotNull(TkGenerationTaskDO::getSubtitleUrl).ne(TkGenerationTaskDO::getSubtitleUrl, "")
                        .or().isNotNull(TkGenerationTaskDO::getSubtitleTimelineUrl).ne(TkGenerationTaskDO::getSubtitleTimelineUrl, "")
                        .or().isNotNull(TkGenerationTaskDO::getSubtitleVisualAnalysisUrl).ne(TkGenerationTaskDO::getSubtitleVisualAnalysisUrl, "")
                        .or().isNotNull(TkGenerationTaskDO::getSubtitleLayoutUrl).ne(TkGenerationTaskDO::getSubtitleLayoutUrl, "")
                        .or().isNotNull(TkGenerationTaskDO::getSubtitleAssUrl).ne(TkGenerationTaskDO::getSubtitleAssUrl, ""))
                .orderByAsc(TkGenerationTaskDO::getId)
                .last("LIMIT " + limit));
    }

    default PageResult<TkGenerationTaskDO> selectPage(TkGenerationTaskPageReqVO reqVO, TkUserScope scope) {
        return selectPage(reqVO, new LambdaQueryWrapperX<TkGenerationTaskDO>()
                .eqIfPresent(TkGenerationTaskDO::getId, reqVO.getId())
                .eqIfPresent(TkGenerationTaskDO::getBatchId, reqVO.getBatchId())
                .eqIfPresent(TkGenerationTaskDO::getTenantId, scope.isGlobalPlatformView() ? null : scope.getTenantId())
                .eqIfPresent(TkGenerationTaskDO::getCompanyId, scope.isPlatformAdmin() ? reqVO.getCompanyId() : null)
                .eqIfPresent(TkGenerationTaskDO::getCreator, scope.canReadAllTenantRecords() ? null : scope.getUserIdString())
                .eqIfPresent(TkGenerationTaskDO::getLibraryId, reqVO.getLibraryId())
                .eqIfPresent(TkGenerationTaskDO::getBusinessTraceId, reqVO.getBusinessTraceId())
                .eqIfPresent(TkGenerationTaskDO::getStatus, reqVO.getStatus())
                .likeIfPresent(TkGenerationTaskDO::getTitle, reqVO.getTitle())
                .orderByDesc(TkGenerationTaskDO::getId));
    }

    default PageResult<TkGenerationTaskDO> selectSummaryPage(TkGenerationTaskPageReqVO reqVO, TkUserScope scope) {
        LambdaQueryWrapperX<TkGenerationTaskDO> wrapper = new LambdaQueryWrapperX<>();
        wrapper.select(TkGenerationTaskDO::getId,
                TkGenerationTaskDO::getTenantId,
                TkGenerationTaskDO::getCreator,
                TkGenerationTaskDO::getBusinessTraceId,
                TkGenerationTaskDO::getBatchId,
                TkGenerationTaskDO::getScriptIndex,
                TkGenerationTaskDO::getVideoIndex,
                TkGenerationTaskDO::getCompanyId,
                TkGenerationTaskDO::getSourceUrl,
                TkGenerationTaskDO::getLibraryId,
                TkGenerationTaskDO::getMaterialPurpose,
                TkGenerationTaskDO::getProductCategoryCode,
                TkGenerationTaskDO::getGenerationRouteCode,
                TkGenerationTaskDO::getVoiceEnabled,
                TkGenerationTaskDO::getBgmEnabled,
                TkGenerationTaskDO::getSubtitleEnabled,
                TkGenerationTaskDO::getOpeningVideoName,
                TkGenerationTaskDO::getReferenceDuration,
                TkGenerationTaskDO::getTargetDuration,
                TkGenerationTaskDO::getStatus,
                TkGenerationTaskDO::getProgress,
                TkGenerationTaskDO::getOutputUrl,
                TkGenerationTaskDO::getFailReason,
                TkGenerationTaskDO::getFailCode,
                TkGenerationTaskDO::getCurrentStep,
                TkGenerationTaskDO::getRetryCount,
                TkGenerationTaskDO::getWorkerId,
                TkGenerationTaskDO::getHeartbeatTime,
                TkGenerationTaskDO::getTitle,
                TkGenerationTaskDO::getCreateTime);
        return selectPage(reqVO, wrapper
                .eqIfPresent(TkGenerationTaskDO::getId, reqVO.getId())
                .eqIfPresent(TkGenerationTaskDO::getBatchId, reqVO.getBatchId())
                .eqIfPresent(TkGenerationTaskDO::getTenantId, scope.isGlobalPlatformView() ? null : scope.getTenantId())
                .eqIfPresent(TkGenerationTaskDO::getCompanyId, scope.isPlatformAdmin() ? reqVO.getCompanyId() : null)
                .eqIfPresent(TkGenerationTaskDO::getCreator, scope.canReadAllTenantRecords() ? null : scope.getUserIdString())
                .eqIfPresent(TkGenerationTaskDO::getLibraryId, reqVO.getLibraryId())
                .eqIfPresent(TkGenerationTaskDO::getBusinessTraceId, reqVO.getBusinessTraceId())
                .eqIfPresent(TkGenerationTaskDO::getStatus, reqVO.getStatus())
                .likeIfPresent(TkGenerationTaskDO::getTitle, reqVO.getTitle())
                .orderByDesc(TkGenerationTaskDO::getId));
    }

    default List<Long> selectDailyTaskIds(Long tenantId, String creator, LocalDateTime startTime,
                                          LocalDateTime endTime) {
        LambdaQueryWrapperX<TkGenerationTaskDO> wrapper = new LambdaQueryWrapperX<>();
        wrapper.select(TkGenerationTaskDO::getId);
        wrapper.eqIfPresent(TkGenerationTaskDO::getTenantId, tenantId)
                .eq(TkGenerationTaskDO::getCreator, creator);
        wrapper.ge(TkGenerationTaskDO::getCreateTime, startTime)
                .lt(TkGenerationTaskDO::getCreateTime, endTime);
        wrapper.orderByAsc(TkGenerationTaskDO::getCreateTime)
                .orderByAsc(TkGenerationTaskDO::getId);
        return selectList(wrapper)
                .stream()
                .map(TkGenerationTaskDO::getId)
                .collect(Collectors.toList());
    }

    default List<TkGenerationTaskDO> selectDailySequenceCandidates(Collection<Long> tenantIds,
                                                                    Collection<String> creators,
                                                                    LocalDateTime startTime,
                                                                    LocalDateTime endTime) {
        LambdaQueryWrapperX<TkGenerationTaskDO> wrapper = new LambdaQueryWrapperX<>();
        wrapper.select(TkGenerationTaskDO::getId, TkGenerationTaskDO::getTenantId,
                TkGenerationTaskDO::getCreator, TkGenerationTaskDO::getCreateTime);
        return selectList(wrapper
                .inIfPresent(TkGenerationTaskDO::getTenantId, tenantIds)
                .inIfPresent(TkGenerationTaskDO::getCreator, creators)
                .ge(TkGenerationTaskDO::getCreateTime, startTime)
                .lt(TkGenerationTaskDO::getCreateTime, endTime)
                .orderByAsc(TkGenerationTaskDO::getCreateTime)
                .orderByAsc(TkGenerationTaskDO::getId));
    }

    default List<TkGenerationTaskDO> selectStatusBatch(Collection<Long> ids, TkUserScope scope) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        LambdaQueryWrapperX<TkGenerationTaskDO> wrapper = new LambdaQueryWrapperX<>();
        wrapper.select(TkGenerationTaskDO::getId,
                TkGenerationTaskDO::getTenantId,
                TkGenerationTaskDO::getCompanyId,
                TkGenerationTaskDO::getCreator,
                TkGenerationTaskDO::getBatchId,
                TkGenerationTaskDO::getScriptIndex,
                TkGenerationTaskDO::getVideoIndex,
                TkGenerationTaskDO::getProductCategoryCode,
                TkGenerationTaskDO::getGenerationRouteCode,
                TkGenerationTaskDO::getStatus,
                TkGenerationTaskDO::getProgress,
                TkGenerationTaskDO::getOutputUrl,
                TkGenerationTaskDO::getFailReason,
                TkGenerationTaskDO::getFailCode,
                TkGenerationTaskDO::getCurrentStep,
                TkGenerationTaskDO::getHeartbeatTime,
                TkGenerationTaskDO::getStepStartedAt,
                TkGenerationTaskDO::getStepFinishedAt);
        return selectList(wrapper
                .in(TkGenerationTaskDO::getId, ids)
                .eqIfPresent(TkGenerationTaskDO::getTenantId, scope.isGlobalPlatformView() ? null : scope.getTenantId())
                .eqIfPresent(TkGenerationTaskDO::getCompanyId, scope.isPlatformAdmin() ? null : scope.getCompanyId())
                .eqIfPresent(TkGenerationTaskDO::getCreator, scope.canReadAllTenantRecords() ? null : scope.getUserIdString()));
    }

    default Long selectCount(TkUserScope scope) {
        return selectCount(new LambdaQueryWrapperX<TkGenerationTaskDO>()
                .eqIfPresent(TkGenerationTaskDO::getTenantId, scope.isGlobalPlatformView() ? null : scope.getTenantId())
                .eqIfPresent(TkGenerationTaskDO::getCreator, scope.canReadAllTenantRecords() ? null : scope.getUserIdString()));
    }

    default Long selectCount(TkUserScope scope, LocalDateTime startTime, LocalDateTime endTime) {
        return selectCount(new LambdaQueryWrapperX<TkGenerationTaskDO>()
                .eqIfPresent(TkGenerationTaskDO::getTenantId, scope.isGlobalPlatformView() ? null : scope.getTenantId())
                .eqIfPresent(TkGenerationTaskDO::getCreator, scope.canReadAllTenantRecords() ? null : scope.getUserIdString())
                .geIfPresent(TkGenerationTaskDO::getCreateTime, startTime)
                .ltIfPresent(TkGenerationTaskDO::getCreateTime, endTime));
    }

    default Long selectAnalyticsCount(TkUserScope scope, LocalDateTime startTime, LocalDateTime endTime,
                                      Long libraryId, String targetLanguage, String status) {
        return selectCount(new LambdaQueryWrapperX<TkGenerationTaskDO>()
                .eqIfPresent(TkGenerationTaskDO::getTenantId, scope.isGlobalPlatformView() ? null : scope.getTenantId())
                .eqIfPresent(TkGenerationTaskDO::getCreator, scope.canReadAllTenantRecords() ? null : scope.getUserIdString())
                .geIfPresent(TkGenerationTaskDO::getCreateTime, startTime)
                .ltIfPresent(TkGenerationTaskDO::getCreateTime, endTime)
                .eqIfPresent(TkGenerationTaskDO::getLibraryId, libraryId)
                .eqIfPresent(TkGenerationTaskDO::getTargetLanguage, targetLanguage)
                .eqIfPresent(TkGenerationTaskDO::getStatus, status));
    }

    default List<TkGenerationTaskDO> selectRecentFailures(TkUserScope scope, LocalDateTime startTime, LocalDateTime endTime,
                                                          Long libraryId, String targetLanguage, int limit) {
        LambdaQueryWrapperX<TkGenerationTaskDO> wrapper = new LambdaQueryWrapperX<>();
        wrapper.select(TkGenerationTaskDO::getId,
                TkGenerationTaskDO::getTenantId,
                TkGenerationTaskDO::getBusinessTraceId,
                TkGenerationTaskDO::getCompanyId,
                TkGenerationTaskDO::getSourceUrl,
                TkGenerationTaskDO::getLibraryId,
                TkGenerationTaskDO::getStatus,
                TkGenerationTaskDO::getProgress,
                TkGenerationTaskDO::getOutputUrl,
                TkGenerationTaskDO::getFailReason,
                TkGenerationTaskDO::getFailCode,
                TkGenerationTaskDO::getCurrentStep,
                TkGenerationTaskDO::getRetryCount,
                TkGenerationTaskDO::getTitle,
                TkGenerationTaskDO::getTargetLanguage,
                TkGenerationTaskDO::getCreateTime);
        return selectList(wrapper
                .eqIfPresent(TkGenerationTaskDO::getTenantId, scope.isGlobalPlatformView() ? null : scope.getTenantId())
                .eqIfPresent(TkGenerationTaskDO::getCreator, scope.canReadAllTenantRecords() ? null : scope.getUserIdString())
                .geIfPresent(TkGenerationTaskDO::getCreateTime, startTime)
                .ltIfPresent(TkGenerationTaskDO::getCreateTime, endTime)
                .eqIfPresent(TkGenerationTaskDO::getLibraryId, libraryId)
                .eqIfPresent(TkGenerationTaskDO::getTargetLanguage, targetLanguage)
                .eq(TkGenerationTaskDO::getStatus, TkGenerationStatusEnum.FAILED)
                .orderByDesc(TkGenerationTaskDO::getId)
                .last("LIMIT " + limit));
    }

    default List<TkGenerationTaskDO> selectFailureDiagnosisSamples(TkUserScope scope, LocalDateTime startTime,
                                                                   LocalDateTime endTime, Long libraryId,
                                                                   String targetLanguage, int limit) {
        LambdaQueryWrapperX<TkGenerationTaskDO> wrapper = new LambdaQueryWrapperX<>();
        wrapper.select(TkGenerationTaskDO::getId,
                TkGenerationTaskDO::getFailReason,
                TkGenerationTaskDO::getFailCode,
                TkGenerationTaskDO::getCurrentStep);
        return selectList(wrapper
                .eqIfPresent(TkGenerationTaskDO::getTenantId, scope.isGlobalPlatformView() ? null : scope.getTenantId())
                .eqIfPresent(TkGenerationTaskDO::getCreator, scope.canReadAllTenantRecords() ? null : scope.getUserIdString())
                .geIfPresent(TkGenerationTaskDO::getCreateTime, startTime)
                .ltIfPresent(TkGenerationTaskDO::getCreateTime, endTime)
                .eqIfPresent(TkGenerationTaskDO::getLibraryId, libraryId)
                .eqIfPresent(TkGenerationTaskDO::getTargetLanguage, targetLanguage)
                .eq(TkGenerationTaskDO::getStatus, TkGenerationStatusEnum.FAILED)
                .orderByDesc(TkGenerationTaskDO::getId)
                .last("LIMIT " + limit));
    }

    default List<TkGenerationTaskDO> selectListByBatchId(Long batchId, TkUserScope scope) {
        return selectList(new LambdaQueryWrapperX<TkGenerationTaskDO>()
                .eq(TkGenerationTaskDO::getBatchId, batchId)
                .eqIfPresent(TkGenerationTaskDO::getTenantId, scope.isGlobalPlatformView() ? null : scope.getTenantId())
                .eqIfPresent(TkGenerationTaskDO::getCreator, scope.canReadAllTenantRecords() ? null : scope.getUserIdString())
                .orderByAsc(TkGenerationTaskDO::getScriptIndex)
                .orderByAsc(TkGenerationTaskDO::getVideoIndex)
                .orderByAsc(TkGenerationTaskDO::getId));
    }

    @Select("<script>"
            + "SELECT status AS name, COUNT(*) AS value FROM tk_generation_task "
            + "WHERE deleted = 0 "
            + "<if test='tenantId != null'> AND tenant_id = #{tenantId} </if>"
            + "<if test='creator != null and creator != \"\"'> AND creator = #{creator} </if>"
            + "<if test='startTime != null'> AND create_time &gt;= #{startTime} </if>"
            + "<if test='endTime != null'> AND create_time &lt; #{endTime} </if>"
            + "<if test='libraryId != null'> AND library_id = #{libraryId} </if>"
            + "<if test='targetLanguage != null and targetLanguage != \"\"'> AND target_language = #{targetLanguage} </if>"
            + "<if test='status != null and status != \"\"'> AND status = #{status} </if>"
            + "GROUP BY status"
            + "</script>")
    List<TkDashboardCountItemRespVO> selectStatusStats(@Param("tenantId") Long tenantId,
                                                       @Param("creator") String creator,
                                                       @Param("startTime") LocalDateTime startTime,
                                                       @Param("endTime") LocalDateTime endTime,
                                                       @Param("libraryId") Long libraryId,
                                                       @Param("targetLanguage") String targetLanguage,
                                                       @Param("status") String status);

    default List<TkDashboardCountItemRespVO> selectStatusStats(TkUserScope scope, LocalDateTime startTime,
                                                               LocalDateTime endTime, Long libraryId,
                                                               String targetLanguage, String status) {
        return selectStatusStats(scope.isGlobalPlatformView() ? null : scope.getTenantId(),
                scope.canReadAllTenantRecords() ? null : scope.getUserIdString(),
                startTime, endTime, libraryId, targetLanguage, status);
    }

    @Select("<script>"
            + "SELECT "
            + "COALESCE(NULLIF(material_purpose, ''), 'ECOMMERCE') AS materialPurpose, "
            + "COALESCE(NULLIF(product_category_code, ''), 'DEFAULT') AS productCategoryCode, "
            + "COALESCE(NULLIF(generation_route_code, ''), 'UNKNOWN') AS routeCode, "
            + "COUNT(*) AS generationCount, "
            + "SUM(CASE WHEN status = 'SUCCESS' THEN 1 ELSE 0 END) AS successCount, "
            + "SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) AS failedCount, "
            + "SUM(CASE WHEN status NOT IN ('SUCCESS', 'FAILED') THEN 1 ELSE 0 END) AS runningCount, "
            + "COALESCE(ROUND(100 * SUM(CASE WHEN status = 'SUCCESS' THEN 1 ELSE 0 END) / COUNT(*), 2), 0) AS successRate, "
            + "COALESCE(ROUND(AVG(CASE WHEN step_finished_at IS NOT NULL THEN TIMESTAMPDIFF(SECOND, create_time, step_finished_at) END)), 0) AS averageDurationSeconds "
            + "FROM tk_generation_task WHERE deleted = 0 "
            + "<if test='tenantId != null'> AND tenant_id = #{tenantId} </if>"
            + "<if test='creator != null and creator != \"\"'> AND creator = #{creator} </if>"
            + "<if test='startTime != null'> AND create_time &gt;= #{startTime} </if>"
            + "<if test='endTime != null'> AND create_time &lt; #{endTime} </if>"
            + "<if test='materialPurpose != null and materialPurpose != \"\"'> AND material_purpose = #{materialPurpose} </if>"
            + "<if test='productCategoryCode != null and productCategoryCode != \"\"'> AND product_category_code = #{productCategoryCode} </if>"
            + "<if test='routeCode != null and routeCode != \"\"'> AND generation_route_code = #{routeCode} </if>"
            + "GROUP BY COALESCE(NULLIF(material_purpose, ''), 'ECOMMERCE'), "
            + "COALESCE(NULLIF(product_category_code, ''), 'DEFAULT'), "
            + "COALESCE(NULLIF(generation_route_code, ''), 'UNKNOWN') "
            + "ORDER BY generationCount DESC, routeCode ASC"
            + "</script>")
    List<TkGenerationRouteStatisticsRespVO> selectRouteStatistics(@Param("tenantId") Long tenantId,
                                                                  @Param("creator") String creator,
                                                                  @Param("startTime") LocalDateTime startTime,
                                                                  @Param("endTime") LocalDateTime endTime,
                                                                  @Param("materialPurpose") String materialPurpose,
                                                                  @Param("productCategoryCode") String productCategoryCode,
                                                                  @Param("routeCode") String routeCode);

    @Select("<script>"
            + "SELECT COALESCE(NULLIF(fail_code, ''), 'UNKNOWN') AS name, COUNT(*) AS value "
            + "FROM tk_generation_task WHERE deleted = 0 AND status = 'FAILED' "
            + "<if test='tenantId != null'> AND tenant_id = #{tenantId} </if>"
            + "<if test='creator != null and creator != \"\"'> AND creator = #{creator} </if>"
            + "<if test='startTime != null'> AND create_time &gt;= #{startTime} </if>"
            + "<if test='endTime != null'> AND create_time &lt; #{endTime} </if>"
            + "<if test='libraryId != null'> AND library_id = #{libraryId} </if>"
            + "<if test='targetLanguage != null and targetLanguage != \"\"'> AND target_language = #{targetLanguage} </if>"
            + "GROUP BY COALESCE(NULLIF(fail_code, ''), 'UNKNOWN') ORDER BY value DESC LIMIT #{limit}"
            + "</script>")
    List<TkDashboardCountItemRespVO> selectFailureReasonStats(@Param("tenantId") Long tenantId,
                                                              @Param("creator") String creator,
                                                              @Param("startTime") LocalDateTime startTime,
                                                              @Param("endTime") LocalDateTime endTime,
                                                              @Param("libraryId") Long libraryId,
                                                              @Param("targetLanguage") String targetLanguage,
                                                              @Param("limit") int limit);

    default List<TkDashboardCountItemRespVO> selectFailureReasonStats(TkUserScope scope, LocalDateTime startTime,
                                                                      LocalDateTime endTime, Long libraryId,
                                                                      String targetLanguage, int limit) {
        return selectFailureReasonStats(scope.isGlobalPlatformView() ? null : scope.getTenantId(),
                scope.canReadAllTenantRecords() ? null : scope.getUserIdString(),
                startTime, endTime, libraryId, targetLanguage, limit);
    }

    @Select("<script>"
            + "SELECT COALESCE(NULLIF(current_step, ''), 'UNKNOWN') AS name, COUNT(*) AS value "
            + "FROM tk_generation_task WHERE deleted = 0 AND status = 'FAILED' "
            + "<if test='tenantId != null'> AND tenant_id = #{tenantId} </if>"
            + "<if test='creator != null and creator != \"\"'> AND creator = #{creator} </if>"
            + "<if test='startTime != null'> AND create_time &gt;= #{startTime} </if>"
            + "<if test='endTime != null'> AND create_time &lt; #{endTime} </if>"
            + "<if test='libraryId != null'> AND library_id = #{libraryId} </if>"
            + "<if test='targetLanguage != null and targetLanguage != \"\"'> AND target_language = #{targetLanguage} </if>"
            + "GROUP BY COALESCE(NULLIF(current_step, ''), 'UNKNOWN') ORDER BY value DESC LIMIT #{limit}"
            + "</script>")
    List<TkDashboardCountItemRespVO> selectFailureStepStats(@Param("tenantId") Long tenantId,
                                                            @Param("creator") String creator,
                                                            @Param("startTime") LocalDateTime startTime,
                                                            @Param("endTime") LocalDateTime endTime,
                                                            @Param("libraryId") Long libraryId,
                                                            @Param("targetLanguage") String targetLanguage,
                                                            @Param("limit") int limit);

    default List<TkDashboardCountItemRespVO> selectFailureStepStats(TkUserScope scope, LocalDateTime startTime,
                                                                    LocalDateTime endTime, Long libraryId,
                                                                    String targetLanguage, int limit) {
        return selectFailureStepStats(scope.isGlobalPlatformView() ? null : scope.getTenantId(),
                scope.canReadAllTenantRecords() ? null : scope.getUserIdString(),
                startTime, endTime, libraryId, targetLanguage, limit);
    }

    @Select("<script>"
            + "SELECT DATE(create_time) AS day, COUNT(*) AS totalCount, "
            + "SUM(CASE WHEN status = 'SUCCESS' THEN 1 ELSE 0 END) AS successCount, "
            + "SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) AS failedCount, "
            + "SUM(CASE WHEN status NOT IN ('SUCCESS', 'FAILED') THEN 1 ELSE 0 END) AS runningCount, "
            + "COALESCE(ROUND(AVG(CASE WHEN step_finished_at IS NOT NULL THEN TIMESTAMPDIFF(SECOND, create_time, step_finished_at) END)), 0) AS averageDurationSeconds "
            + "FROM tk_generation_task WHERE deleted = 0 "
            + "<if test='tenantId != null'> AND tenant_id = #{tenantId} </if>"
            + "<if test='creator != null and creator != \"\"'> AND creator = #{creator} </if>"
            + "<if test='startTime != null'> AND create_time &gt;= #{startTime} </if>"
            + "<if test='endTime != null'> AND create_time &lt; #{endTime} </if>"
            + "<if test='libraryId != null'> AND library_id = #{libraryId} </if>"
            + "<if test='targetLanguage != null and targetLanguage != \"\"'> AND target_language = #{targetLanguage} </if>"
            + "<if test='status != null and status != \"\"'> AND status = #{status} </if>"
            + "GROUP BY DATE(create_time) ORDER BY day"
            + "</script>")
    List<TkDashboardTrendRespVO.TrendItem> selectGenerationTrend(@Param("tenantId") Long tenantId,
                                                                 @Param("creator") String creator,
                                                                 @Param("startTime") LocalDateTime startTime,
                                                                 @Param("endTime") LocalDateTime endTime,
                                                                 @Param("libraryId") Long libraryId,
                                                                 @Param("targetLanguage") String targetLanguage,
                                                                 @Param("status") String status);

    default List<TkDashboardTrendRespVO.TrendItem> selectGenerationTrend(TkUserScope scope, LocalDateTime startTime,
                                                                         LocalDateTime endTime, Long libraryId,
                                                                         String targetLanguage, String status) {
        return selectGenerationTrend(scope.isGlobalPlatformView() ? null : scope.getTenantId(),
                scope.canReadAllTenantRecords() ? null : scope.getUserIdString(),
                startTime, endTime, libraryId, targetLanguage, status);
    }

    @Select("<script>"
            + "SELECT COUNT(*) FROM tk_generation_task WHERE deleted = 0 AND status = 'PENDING' "
            + "<if test='tenantId != null'> AND tenant_id = #{tenantId} </if>"
            + "<if test='creator != null and creator != \"\"'> AND creator = #{creator} </if>"
            + "<if test='startTime != null'> AND create_time &gt;= #{startTime} </if>"
            + "<if test='endTime != null'> AND create_time &lt; #{endTime} </if>"
            + "<if test='libraryId != null'> AND library_id = #{libraryId} </if>"
            + "<if test='targetLanguage != null and targetLanguage != \"\"'> AND target_language = #{targetLanguage} </if>"
            + "<if test='status != null and status != \"\"'> AND status = #{status} </if>"
            + "</script>")
    Long selectPendingQueueCount(@Param("tenantId") Long tenantId,
                                 @Param("creator") String creator,
                                 @Param("startTime") LocalDateTime startTime,
                                 @Param("endTime") LocalDateTime endTime,
                                 @Param("libraryId") Long libraryId,
                                 @Param("targetLanguage") String targetLanguage,
                                 @Param("status") String status);

    default Long selectPendingQueueCount(TkUserScope scope, LocalDateTime startTime, LocalDateTime endTime,
                                         Long libraryId, String targetLanguage, String status) {
        return selectPendingQueueCount(scope.isGlobalPlatformView() ? null : scope.getTenantId(),
                scope.canReadAllTenantRecords() ? null : scope.getUserIdString(),
                startTime, endTime, libraryId, targetLanguage, status);
    }

    @Select("<script>"
            + "SELECT COUNT(*) FROM tk_generation_task WHERE deleted = 0 "
            + "AND status IN ('PRECHECKED','ANALYZING','SCRIPT_READY','VOICE_READY','MATERIAL_MATCHING',"
            + "'MATERIAL_MATCHED','SUBTITLE_TIMELINE_READY','VISUAL_ANALYZED','CLIP_PLANNED','RENDERING','EXPORTING') "
            + "<if test='tenantId != null'> AND tenant_id = #{tenantId} </if>"
            + "<if test='creator != null and creator != \"\"'> AND creator = #{creator} </if>"
            + "<if test='startTime != null'> AND create_time &gt;= #{startTime} </if>"
            + "<if test='endTime != null'> AND create_time &lt; #{endTime} </if>"
            + "<if test='libraryId != null'> AND library_id = #{libraryId} </if>"
            + "<if test='targetLanguage != null and targetLanguage != \"\"'> AND target_language = #{targetLanguage} </if>"
            + "<if test='status != null and status != \"\"'> AND status = #{status} </if>"
            + "</script>")
    Long selectRunningQueueCount(@Param("tenantId") Long tenantId,
                                 @Param("creator") String creator,
                                 @Param("startTime") LocalDateTime startTime,
                                 @Param("endTime") LocalDateTime endTime,
                                 @Param("libraryId") Long libraryId,
                                 @Param("targetLanguage") String targetLanguage,
                                 @Param("status") String status);

    default Long selectRunningQueueCount(TkUserScope scope, LocalDateTime startTime, LocalDateTime endTime,
                                         Long libraryId, String targetLanguage, String status) {
        return selectRunningQueueCount(scope.isGlobalPlatformView() ? null : scope.getTenantId(),
                scope.canReadAllTenantRecords() ? null : scope.getUserIdString(),
                startTime, endTime, libraryId, targetLanguage, status);
    }

    @Select("<script>"
            + "SELECT COUNT(*) FROM tk_generation_task WHERE deleted = 0 "
            + "AND status IN ('PRECHECKED','ANALYZING','SCRIPT_READY','VOICE_READY','MATERIAL_MATCHING',"
            + "'MATERIAL_MATCHED','SUBTITLE_TIMELINE_READY','VISUAL_ANALYZED','CLIP_PLANNED','RENDERING','EXPORTING') "
            + "AND (heartbeat_time IS NULL OR heartbeat_time &lt; #{staleBefore}) "
            + "<if test='tenantId != null'> AND tenant_id = #{tenantId} </if>"
            + "<if test='creator != null and creator != \"\"'> AND creator = #{creator} </if>"
            + "<if test='startTime != null'> AND create_time &gt;= #{startTime} </if>"
            + "<if test='endTime != null'> AND create_time &lt; #{endTime} </if>"
            + "<if test='libraryId != null'> AND library_id = #{libraryId} </if>"
            + "<if test='targetLanguage != null and targetLanguage != \"\"'> AND target_language = #{targetLanguage} </if>"
            + "<if test='status != null and status != \"\"'> AND status = #{status} </if>"
            + "</script>")
    Long selectStaleRunningCount(@Param("tenantId") Long tenantId,
                                 @Param("creator") String creator,
                                 @Param("startTime") LocalDateTime startTime,
                                 @Param("endTime") LocalDateTime endTime,
                                 @Param("libraryId") Long libraryId,
                                 @Param("targetLanguage") String targetLanguage,
                                 @Param("status") String status,
                                 @Param("staleBefore") LocalDateTime staleBefore);

    default Long selectStaleRunningCount(TkUserScope scope, LocalDateTime startTime, LocalDateTime endTime,
                                         Long libraryId, String targetLanguage, String status,
                                         LocalDateTime staleBefore) {
        return selectStaleRunningCount(scope.isGlobalPlatformView() ? null : scope.getTenantId(),
                scope.canReadAllTenantRecords() ? null : scope.getUserIdString(),
                startTime, endTime, libraryId, targetLanguage, status, staleBefore);
    }

    @Select("<script>"
            + "SELECT COALESCE(ROUND(AVG(TIMESTAMPDIFF(SECOND, create_time, NOW()))), 0) "
            + "FROM tk_generation_task WHERE deleted = 0 AND status = 'PENDING' "
            + "<if test='tenantId != null'> AND tenant_id = #{tenantId} </if>"
            + "<if test='creator != null and creator != \"\"'> AND creator = #{creator} </if>"
            + "<if test='startTime != null'> AND create_time &gt;= #{startTime} </if>"
            + "<if test='endTime != null'> AND create_time &lt; #{endTime} </if>"
            + "<if test='libraryId != null'> AND library_id = #{libraryId} </if>"
            + "<if test='targetLanguage != null and targetLanguage != \"\"'> AND target_language = #{targetLanguage} </if>"
            + "<if test='status != null and status != \"\"'> AND status = #{status} </if>"
            + "</script>")
    Long selectAveragePendingSeconds(@Param("tenantId") Long tenantId,
                                     @Param("creator") String creator,
                                     @Param("startTime") LocalDateTime startTime,
                                     @Param("endTime") LocalDateTime endTime,
                                     @Param("libraryId") Long libraryId,
                                     @Param("targetLanguage") String targetLanguage,
                                     @Param("status") String status);

    default Long selectAveragePendingSeconds(TkUserScope scope, LocalDateTime startTime, LocalDateTime endTime,
                                             Long libraryId, String targetLanguage, String status) {
        return selectAveragePendingSeconds(scope.isGlobalPlatformView() ? null : scope.getTenantId(),
                scope.canReadAllTenantRecords() ? null : scope.getUserIdString(),
                startTime, endTime, libraryId, targetLanguage, status);
    }

    @Select("<script>"
            + "SELECT COALESCE(ROUND(AVG(TIMESTAMPDIFF(SECOND, COALESCE(step_started_at, create_time), NOW()))), 0) "
            + "FROM tk_generation_task WHERE deleted = 0 "
            + "AND status IN ('PRECHECKED','ANALYZING','SCRIPT_READY','VOICE_READY','MATERIAL_MATCHING',"
            + "'MATERIAL_MATCHED','SUBTITLE_TIMELINE_READY','VISUAL_ANALYZED','CLIP_PLANNED','RENDERING','EXPORTING') "
            + "<if test='tenantId != null'> AND tenant_id = #{tenantId} </if>"
            + "<if test='creator != null and creator != \"\"'> AND creator = #{creator} </if>"
            + "<if test='startTime != null'> AND create_time &gt;= #{startTime} </if>"
            + "<if test='endTime != null'> AND create_time &lt; #{endTime} </if>"
            + "<if test='libraryId != null'> AND library_id = #{libraryId} </if>"
            + "<if test='targetLanguage != null and targetLanguage != \"\"'> AND target_language = #{targetLanguage} </if>"
            + "<if test='status != null and status != \"\"'> AND status = #{status} </if>"
            + "</script>")
    Long selectAverageRunningSeconds(@Param("tenantId") Long tenantId,
                                     @Param("creator") String creator,
                                     @Param("startTime") LocalDateTime startTime,
                                     @Param("endTime") LocalDateTime endTime,
                                     @Param("libraryId") Long libraryId,
                                     @Param("targetLanguage") String targetLanguage,
                                     @Param("status") String status);

    default Long selectAverageRunningSeconds(TkUserScope scope, LocalDateTime startTime, LocalDateTime endTime,
                                             Long libraryId, String targetLanguage, String status) {
        return selectAverageRunningSeconds(scope.isGlobalPlatformView() ? null : scope.getTenantId(),
                scope.canReadAllTenantRecords() ? null : scope.getUserIdString(),
                startTime, endTime, libraryId, targetLanguage, status);
    }

    @Select("<script>"
            + "SELECT id, tenant_id, business_trace_id, company_id, source_url, library_id, status, progress, "
            + "output_url, fail_reason, fail_code, current_step, retry_count, worker_id, heartbeat_time, title, create_time "
            + "FROM tk_generation_task WHERE deleted = 0 "
            + "AND (status = 'PENDING' OR (status IN ('PRECHECKED','ANALYZING','SCRIPT_READY','VOICE_READY','MATERIAL_MATCHING',"
            + "'MATERIAL_MATCHED','SUBTITLE_TIMELINE_READY','VISUAL_ANALYZED','CLIP_PLANNED','RENDERING','EXPORTING') "
            + "AND (heartbeat_time IS NULL OR heartbeat_time &lt; #{staleBefore}))) "
            + "<if test='tenantId != null'> AND tenant_id = #{tenantId} </if>"
            + "<if test='creator != null and creator != \"\"'> AND creator = #{creator} </if>"
            + "<if test='startTime != null'> AND create_time &gt;= #{startTime} </if>"
            + "<if test='endTime != null'> AND create_time &lt; #{endTime} </if>"
            + "<if test='libraryId != null'> AND library_id = #{libraryId} </if>"
            + "<if test='targetLanguage != null and targetLanguage != \"\"'> AND target_language = #{targetLanguage} </if>"
            + "<if test='status != null and status != \"\"'> AND status = #{status} </if>"
            + "ORDER BY CASE WHEN status = 'PENDING' THEN 0 ELSE 1 END, heartbeat_time ASC, id ASC LIMIT #{limit}"
            + "</script>")
    List<TkGenerationTaskDO> selectQueueAttentionTasks(@Param("tenantId") Long tenantId,
                                                       @Param("creator") String creator,
                                                       @Param("startTime") LocalDateTime startTime,
                                                       @Param("endTime") LocalDateTime endTime,
                                                       @Param("libraryId") Long libraryId,
                                                       @Param("targetLanguage") String targetLanguage,
                                                       @Param("status") String status,
                                                       @Param("staleBefore") LocalDateTime staleBefore,
                                                       @Param("limit") int limit);

    default List<TkGenerationTaskDO> selectQueueAttentionTasks(TkUserScope scope, LocalDateTime startTime,
                                                               LocalDateTime endTime, Long libraryId,
                                                               String targetLanguage, String status,
                                                               LocalDateTime staleBefore, int limit) {
        return selectQueueAttentionTasks(scope.isGlobalPlatformView() ? null : scope.getTenantId(),
                scope.canReadAllTenantRecords() ? null : scope.getUserIdString(),
                startTime, endTime, libraryId, targetLanguage, status, staleBefore, limit);
    }

    @Select("<script>"
            + "SELECT id AS taskId, title, status, current_step AS currentStep, fail_code AS failCode, "
            + "fail_reason AS failReason, "
            + "TIMESTAMPDIFF(SECOND, create_time, COALESCE(step_finished_at, update_time, NOW())) AS durationSeconds, "
            + "CASE WHEN status = 'PENDING' THEN 'QUEUE' "
            + "WHEN status IN ('PRECHECKED','ANALYZING','SCRIPT_READY','VOICE_READY','MATERIAL_MATCHING',"
            + "'MATERIAL_MATCHED','SUBTITLE_TIMELINE_READY','VISUAL_ANALYZED','CLIP_PLANNED','RENDERING','EXPORTING') "
            + "THEN 'RUNNING' ELSE 'TOTAL' END AS durationType, "
            + "create_time AS createTime, heartbeat_time AS heartbeatTime "
            + "FROM tk_generation_task WHERE deleted = 0 "
            + "<if test='tenantId != null'> AND tenant_id = #{tenantId} </if>"
            + "<if test='creator != null and creator != \"\"'> AND creator = #{creator} </if>"
            + "<if test='startTime != null'> AND create_time &gt;= #{startTime} </if>"
            + "<if test='endTime != null'> AND create_time &lt; #{endTime} </if>"
            + "<if test='libraryId != null'> AND library_id = #{libraryId} </if>"
            + "<if test='targetLanguage != null and targetLanguage != \"\"'> AND target_language = #{targetLanguage} </if>"
            + "<if test='status != null and status != \"\"'> AND status = #{status} </if>"
            + "ORDER BY durationSeconds DESC LIMIT #{limit}"
            + "</script>")
    List<TkDashboardSlowTaskRespVO.SlowTaskItem> selectSlowTasks(@Param("tenantId") Long tenantId,
                                                                 @Param("creator") String creator,
                                                                 @Param("startTime") LocalDateTime startTime,
                                                                 @Param("endTime") LocalDateTime endTime,
                                                                 @Param("libraryId") Long libraryId,
                                                                 @Param("targetLanguage") String targetLanguage,
                                                                 @Param("status") String status,
                                                                 @Param("limit") int limit);

    default List<TkDashboardSlowTaskRespVO.SlowTaskItem> selectSlowTasks(TkUserScope scope, LocalDateTime startTime,
                                                                         LocalDateTime endTime, Long libraryId,
                                                                         String targetLanguage, String status,
                                                                         int limit) {
        return selectSlowTasks(scope.isGlobalPlatformView() ? null : scope.getTenantId(),
                scope.canReadAllTenantRecords() ? null : scope.getUserIdString(),
                startTime, endTime, libraryId, targetLanguage, status, limit);
    }

    default List<TkGenerationTaskDO> selectTop5(TkUserScope scope) {
        return selectList(new LambdaQueryWrapperX<TkGenerationTaskDO>()
                .eqIfPresent(TkGenerationTaskDO::getTenantId, scope.isGlobalPlatformView() ? null : scope.getTenantId())
                .eqIfPresent(TkGenerationTaskDO::getCreator, scope.canReadAllTenantRecords() ? null : scope.getUserIdString())
                .orderByDesc(TkGenerationTaskDO::getId)
                .last("LIMIT 5"));
    }

    default List<TkGenerationTaskDO> selectRecentSuccessfulClipPlansByLibraryId(Long libraryId, Long excludeTaskId, int limit) {
        if (libraryId == null || limit <= 0) {
            return Collections.emptyList();
        }
        LambdaQueryWrapperX<TkGenerationTaskDO> wrapper = new LambdaQueryWrapperX<>();
        wrapper.select(TkGenerationTaskDO::getId, TkGenerationTaskDO::getClipPlan);
        return selectList(wrapper
                .eq(TkGenerationTaskDO::getLibraryId, libraryId)
                .eq(TkGenerationTaskDO::getStatus, TkGenerationStatusEnum.SUCCESS)
                .neIfPresent(TkGenerationTaskDO::getId, excludeTaskId)
                .orderByDesc(TkGenerationTaskDO::getId)
                .last("LIMIT " + limit));
    }

    default List<TkGenerationTaskDO> selectPendingForQueue(int limit) {
        return selectList(new LambdaQueryWrapperX<TkGenerationTaskDO>()
                .eq(TkGenerationTaskDO::getStatus, TkGenerationStatusEnum.PENDING)
                .orderByAsc(TkGenerationTaskDO::getId)
                .last("LIMIT " + limit));
    }

    default List<TkGenerationTaskDO> selectStaleRunningForQueue(LocalDateTime staleBefore, int limit) {
        return selectList(new LambdaQueryWrapperX<TkGenerationTaskDO>()
                .in(TkGenerationTaskDO::getStatus, Arrays.asList(
                        TkGenerationStatusEnum.ANALYZING,
                        TkGenerationStatusEnum.SCRIPT_READY,
                        TkGenerationStatusEnum.VOICE_READY,
                        TkGenerationStatusEnum.MATERIAL_MATCHING,
                        TkGenerationStatusEnum.MATERIAL_MATCHED,
                        TkGenerationStatusEnum.SUBTITLE_TIMELINE_READY,
                        TkGenerationStatusEnum.VISUAL_ANALYZED,
                        TkGenerationStatusEnum.CLIP_PLANNED,
                        TkGenerationStatusEnum.RENDERING,
                        TkGenerationStatusEnum.EXPORTING))
                .lt(TkGenerationTaskDO::getHeartbeatTime, staleBefore)
                .orderByAsc(TkGenerationTaskDO::getHeartbeatTime)
                .last("LIMIT " + limit));
    }

}


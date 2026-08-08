package cn.iocoder.yudao.module.tk.dal.mysql;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.tk.controller.admin.dashboard.vo.TkDashboardMaterialHealthRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.material.vo.TkMaterialLibraryPageReqVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkMaterialLibraryDO;
import cn.iocoder.yudao.module.tk.service.scope.TkUserScope;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

import java.util.List;

@Mapper
public interface TkMaterialLibraryMapper extends BaseMapperX<TkMaterialLibraryDO> {

    default PageResult<TkMaterialLibraryDO> selectPage(TkMaterialLibraryPageReqVO reqVO, TkUserScope scope) {
        return selectPage(reqVO, new LambdaQueryWrapperX<TkMaterialLibraryDO>()
                .eqIfPresent(TkMaterialLibraryDO::getTenantId, scope.isGlobalPlatformView() ? null : scope.getTenantId())
                .eqIfPresent(TkMaterialLibraryDO::getCompanyId, scope.isPlatformAdmin() ? reqVO.getCompanyId() : null)
                .likeIfPresent(TkMaterialLibraryDO::getName, reqVO.getName())
                .eqIfPresent(TkMaterialLibraryDO::getCategory, reqVO.getCategory())
                .eqIfPresent(TkMaterialLibraryDO::getMaterialPurpose, reqVO.getMaterialPurpose())
                .eqIfPresent(TkMaterialLibraryDO::getStatus, reqVO.getStatus())
                .orderByDesc(TkMaterialLibraryDO::getId));
    }

    default List<TkMaterialLibraryDO> selectTop5(TkUserScope scope) {
        return selectList(new LambdaQueryWrapperX<TkMaterialLibraryDO>()
                .eqIfPresent(TkMaterialLibraryDO::getTenantId, scope.isGlobalPlatformView() ? null : scope.getTenantId())
                .orderByDesc(TkMaterialLibraryDO::getId)
                .last("LIMIT 5"));
    }

    default Long selectCount(TkUserScope scope) {
        return selectCount(new LambdaQueryWrapperX<TkMaterialLibraryDO>()
                .eqIfPresent(TkMaterialLibraryDO::getTenantId, scope.isGlobalPlatformView() ? null : scope.getTenantId()));
    }

    @Select("<script>"
            + "SELECT l.id AS libraryId, l.name AS libraryName, l.status AS libraryStatus, "
            + "COUNT(v.id) AS videoCount, "
            + "SUM(CASE WHEN v.status = 'AVAILABLE' THEN 1 ELSE 0 END) AS availableVideoCount, "
            + "SUM(CASE WHEN v.status = 'PARSING' THEN 1 ELSE 0 END) AS parsingVideoCount, "
            + "SUM(CASE WHEN v.status = 'FAILED' THEN 1 ELSE 0 END) AS failedVideoCount, "
            + "COALESCE(gt.generationCount, 0) AS generationCount, gt.lastUsedTime AS lastUsedTime "
            + "FROM tk_material_library l "
            + "LEFT JOIN tk_material_video v ON v.library_id = l.id AND v.deleted = 0 "
            + "LEFT JOIN ("
            + "  SELECT library_id, COUNT(*) AS generationCount, MAX(create_time) AS lastUsedTime "
            + "  FROM tk_generation_task WHERE deleted = 0 "
            + "  <if test='tenantId != null'> AND tenant_id = #{tenantId} </if>"
            + "  <if test='creator != null and creator != \"\"'> AND creator = #{creator} </if>"
            + "  <if test='startTime != null'> AND create_time &gt;= #{startTime} </if>"
            + "  <if test='endTime != null'> AND create_time &lt; #{endTime} </if>"
            + "  <if test='targetLanguage != null and targetLanguage != \"\"'> AND target_language = #{targetLanguage} </if>"
            + "  GROUP BY library_id"
            + ") gt ON gt.library_id = l.id "
            + "WHERE l.deleted = 0 "
            + "<if test='tenantId != null'> AND l.tenant_id = #{tenantId} </if>"
            + "<if test='libraryId != null'> AND l.id = #{libraryId} </if>"
            + "GROUP BY l.id, l.name, l.status, gt.generationCount, gt.lastUsedTime "
            + "ORDER BY failedVideoCount DESC, videoCount ASC, generationCount DESC, l.id DESC "
            + "LIMIT #{limit}"
            + "</script>")
    List<TkDashboardMaterialHealthRespVO.LibraryHealthItem> selectHealthStats(@Param("tenantId") Long tenantId,
                                                                              @Param("creator") String creator,
                                                                              @Param("startTime") LocalDateTime startTime,
                                                                              @Param("endTime") LocalDateTime endTime,
                                                                              @Param("libraryId") Long libraryId,
                                                                              @Param("targetLanguage") String targetLanguage,
                                                                              @Param("limit") int limit);

    default List<TkDashboardMaterialHealthRespVO.LibraryHealthItem> selectHealthStats(TkUserScope scope,
                                                                                      LocalDateTime startTime,
                                                                                      LocalDateTime endTime,
                                                                                      Long libraryId,
                                                                                      String targetLanguage,
                                                                                      int limit) {
        return selectHealthStats(scope.isGlobalPlatformView() ? null : scope.getTenantId(),
                scope.canReadAllTenantRecords() ? null : scope.getUserIdString(),
                startTime, endTime, libraryId, targetLanguage, limit);
    }

}


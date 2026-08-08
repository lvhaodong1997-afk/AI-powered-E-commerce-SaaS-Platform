package cn.iocoder.yudao.module.tk.dal.mysql;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.tk.controller.admin.material.vo.TkMaterialVideoPageReqVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkMaterialVideoDO;
import cn.iocoder.yudao.module.tk.enums.TkMaterialVideoStatusEnum;
import cn.iocoder.yudao.module.tk.service.scope.TkUserScope;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface TkMaterialVideoMapper extends BaseMapperX<TkMaterialVideoDO> {

    default PageResult<TkMaterialVideoDO> selectPage(TkMaterialVideoPageReqVO reqVO, TkUserScope scope) {
        return selectPage(reqVO, new LambdaQueryWrapperX<TkMaterialVideoDO>()
                .eqIfPresent(TkMaterialVideoDO::getTenantId, scope.isGlobalPlatformView() ? null : scope.getTenantId())
                .eqIfPresent(TkMaterialVideoDO::getCompanyId, scope.isPlatformAdmin() ? reqVO.getCompanyId() : null)
                .eqIfPresent(TkMaterialVideoDO::getLibraryId, reqVO.getLibraryId())
                .likeIfPresent(TkMaterialVideoDO::getFileName, reqVO.getFileName())
                .eqIfPresent(TkMaterialVideoDO::getStatus, reqVO.getStatus())
                .eqIfPresent(TkMaterialVideoDO::getUsagePhase, reqVO.getUsagePhase())
                .eqIfPresent(TkMaterialVideoDO::getSegmentType, reqVO.getSegmentType())
                .orderByDesc(TkMaterialVideoDO::getId));
    }

    default Long selectCount(TkUserScope scope) {
        return selectCount(new LambdaQueryWrapperX<TkMaterialVideoDO>()
                .eqIfPresent(TkMaterialVideoDO::getTenantId, scope.isGlobalPlatformView() ? null : scope.getTenantId())
                );
    }

    default Long selectCount(TkUserScope scope, Long libraryId) {
        return selectCount(new LambdaQueryWrapperX<TkMaterialVideoDO>()
                .eqIfPresent(TkMaterialVideoDO::getTenantId, scope.isGlobalPlatformView() ? null : scope.getTenantId())
                .eqIfPresent(TkMaterialVideoDO::getLibraryId, libraryId));
    }

    default Long selectCountByStatus(TkUserScope scope, String status) {
        return selectCount(new LambdaQueryWrapperX<TkMaterialVideoDO>()
                .eqIfPresent(TkMaterialVideoDO::getTenantId, scope.isGlobalPlatformView() ? null : scope.getTenantId())
                .eq(TkMaterialVideoDO::getStatus, status));
    }

    default Long selectCountByStatus(TkUserScope scope, String status, Long libraryId) {
        return selectCount(new LambdaQueryWrapperX<TkMaterialVideoDO>()
                .eqIfPresent(TkMaterialVideoDO::getTenantId, scope.isGlobalPlatformView() ? null : scope.getTenantId())
                .eqIfPresent(TkMaterialVideoDO::getLibraryId, libraryId)
                .eq(TkMaterialVideoDO::getStatus, status));
    }

    default boolean existsByLibraryId(Long libraryId) {
        return selectCount(TkMaterialVideoDO::getLibraryId, libraryId) > 0;
    }

    default List<TkMaterialVideoDO> selectListByLibraryId(Long libraryId) {
        return selectList(new LambdaQueryWrapperX<TkMaterialVideoDO>()
                .eq(TkMaterialVideoDO::getLibraryId, libraryId)
                .eq(TkMaterialVideoDO::getStatus, TkMaterialVideoStatusEnum.AVAILABLE)
                .orderByAsc(TkMaterialVideoDO::getId));
    }

    default TkMaterialVideoDO selectFirstByLibraryId(Long libraryId) {
        return selectOne(new LambdaQueryWrapperX<TkMaterialVideoDO>()
                .eq(TkMaterialVideoDO::getLibraryId, libraryId)
                .orderByAsc(TkMaterialVideoDO::getId)
                .last("LIMIT 1"));
    }

}


package cn.iocoder.yudao.module.tk.dal.mysql;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo.TkTiktokContentVideoPageReqVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkTiktokContentVideoDO;
import cn.iocoder.yudao.module.tk.service.scope.TkUserScope;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface TkTiktokContentVideoMapper extends BaseMapperX<TkTiktokContentVideoDO> {

    default PageResult<TkTiktokContentVideoDO> selectPage(TkTiktokContentVideoPageReqVO reqVO, TkUserScope scope) {
        return selectPage(reqVO, new LambdaQueryWrapperX<TkTiktokContentVideoDO>()
                .eqIfPresent(TkTiktokContentVideoDO::getTenantId, scope.isGlobalPlatformView() ? null : scope.getTenantId())
                .eqIfPresent(TkTiktokContentVideoDO::getCompanyId, scope.isPlatformAdmin() ? reqVO.getCompanyId() : null)
                .eqIfPresent(TkTiktokContentVideoDO::getAccountId, reqVO.getAccountId())
                .eqIfPresent(TkTiktokContentVideoDO::getVideoId, reqVO.getVideoId())
                .eqIfPresent(TkTiktokContentVideoDO::getStatus, reqVO.getStatus())
                .and(StrUtil.isNotBlank(reqVO.getKeyword()), wrapper -> wrapper
                        .like(TkTiktokContentVideoDO::getTitle, reqVO.getKeyword())
                        .or().like(TkTiktokContentVideoDO::getVideoDescription, reqVO.getKeyword())
                        .or().like(TkTiktokContentVideoDO::getVideoId, reqVO.getKeyword()))
                .orderByDesc(TkTiktokContentVideoDO::getVideoCreateTime)
                .orderByDesc(TkTiktokContentVideoDO::getId));
    }

    default TkTiktokContentVideoDO selectByAccountIdAndVideoId(Long accountId, String videoId) {
        return selectOne(new LambdaQueryWrapperX<TkTiktokContentVideoDO>()
                .eq(TkTiktokContentVideoDO::getAccountId, accountId)
                .eq(TkTiktokContentVideoDO::getVideoId, videoId));
    }

    default List<TkTiktokContentVideoDO> selectListByAccountId(Long accountId) {
        return selectList(new LambdaQueryWrapperX<TkTiktokContentVideoDO>()
                .eq(TkTiktokContentVideoDO::getAccountId, accountId)
                .orderByDesc(TkTiktokContentVideoDO::getVideoCreateTime));
    }

    default List<TkTiktokContentVideoDO> selectPublicListByAccountIds(Collection<Long> accountIds, TkUserScope scope) {
        return selectList(new LambdaQueryWrapperX<TkTiktokContentVideoDO>()
                .eqIfPresent(TkTiktokContentVideoDO::getTenantId, scope.isGlobalPlatformView() ? null : scope.getTenantId())
                .eqIfPresent(TkTiktokContentVideoDO::getCompanyId,
                        scope.isPlatformAdmin() || scope.isTenantAdmin() ? null : scope.getCompanyId())
                .eqIfPresent(TkTiktokContentVideoDO::getCreator,
                        scope.canReadAllTenantRecords() ? null : scope.getUserIdString())
                .inIfPresent(TkTiktokContentVideoDO::getAccountId, accountIds)
                .eq(TkTiktokContentVideoDO::getStatus, "PUBLIC")
                .orderByDesc(TkTiktokContentVideoDO::getVideoCreateTime)
                .orderByDesc(TkTiktokContentVideoDO::getId));
    }

    default List<TkTiktokContentVideoDO> selectRecentPublicListByAccountId(Long accountId, TkUserScope scope,
                                                                              int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 5));
        return selectList(new LambdaQueryWrapperX<TkTiktokContentVideoDO>()
                .eqIfPresent(TkTiktokContentVideoDO::getTenantId, scope.isGlobalPlatformView() ? null : scope.getTenantId())
                .eqIfPresent(TkTiktokContentVideoDO::getCompanyId,
                        scope.isPlatformAdmin() || scope.isTenantAdmin() ? null : scope.getCompanyId())
                .eqIfPresent(TkTiktokContentVideoDO::getCreator,
                        scope.canReadAllTenantRecords() ? null : scope.getUserIdString())
                .eq(TkTiktokContentVideoDO::getAccountId, accountId)
                .eq(TkTiktokContentVideoDO::getStatus, "PUBLIC")
                .orderByDesc(TkTiktokContentVideoDO::getVideoCreateTime)
                .orderByDesc(TkTiktokContentVideoDO::getId)
                .last("LIMIT " + safeLimit));
    }

}

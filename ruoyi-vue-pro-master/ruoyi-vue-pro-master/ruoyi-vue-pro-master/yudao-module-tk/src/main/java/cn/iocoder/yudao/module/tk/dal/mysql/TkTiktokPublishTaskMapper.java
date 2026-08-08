package cn.iocoder.yudao.module.tk.dal.mysql;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo.TkTiktokPublishTaskPageReqVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkTiktokPublishTaskDO;
import cn.iocoder.yudao.module.tk.service.scope.TkUserScope;
import org.apache.ibatis.annotations.Mapper;

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

}


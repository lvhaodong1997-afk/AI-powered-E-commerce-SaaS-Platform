package cn.iocoder.yudao.module.tk.dal.mysql;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo.TkTiktokPublishDetailPageReqVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkTiktokPublishDetailDO;
import cn.iocoder.yudao.module.tk.service.scope.TkUserScope;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Mapper
public interface TkTiktokPublishDetailMapper extends BaseMapperX<TkTiktokPublishDetailDO> {

    default PageResult<TkTiktokPublishDetailDO> selectPage(TkTiktokPublishDetailPageReqVO reqVO, TkUserScope scope) {
        return selectPage(reqVO, new LambdaQueryWrapperX<TkTiktokPublishDetailDO>()
                .eqIfPresent(TkTiktokPublishDetailDO::getTenantId, scope.isGlobalPlatformView() ? null : scope.getTenantId())
                .eqIfPresent(TkTiktokPublishDetailDO::getCompanyId, scope.isPlatformAdmin() ? reqVO.getCompanyId() : null)
                .eqIfPresent(TkTiktokPublishDetailDO::getCreator, scope.canReadAllTenantRecords() ? null : scope.getUserIdString())
                .eqIfPresent(TkTiktokPublishDetailDO::getPublishTaskId, reqVO.getPublishTaskId())
                .eqIfPresent(TkTiktokPublishDetailDO::getAccountId, reqVO.getAccountId())
                .eqIfPresent(TkTiktokPublishDetailDO::getBusinessTraceId, reqVO.getBusinessTraceId())
                .eqIfPresent(TkTiktokPublishDetailDO::getStatus, reqVO.getStatus())
                .eqIfPresent(TkTiktokPublishDetailDO::getTiktokStatus, reqVO.getTiktokStatus())
                .and(StrUtil.isNotBlank(reqVO.getKeyword()), wrapper -> wrapper
                        .like(TkTiktokPublishDetailDO::getAccountDisplayName, reqVO.getKeyword())
                        .or()
                        .like(TkTiktokPublishDetailDO::getPublishId, reqVO.getKeyword()))
                .orderByDesc(TkTiktokPublishDetailDO::getId));
    }

    default List<TkTiktokPublishDetailDO> selectListByTaskId(Long taskId) {
        return selectList(TkTiktokPublishDetailDO::getPublishTaskId, taskId);
    }

    default TkTiktokPublishDetailDO selectLatestRegisteredTargetByGenerationTaskId(Long generationTaskId) {
        return selectOne(new LambdaQueryWrapperX<TkTiktokPublishDetailDO>()
                .eq(TkTiktokPublishDetailDO::getGenerationTaskId, generationTaskId)
                .orderByDesc(TkTiktokPublishDetailDO::getId)
                .last("LIMIT 1"));
    }

    default TkTiktokPublishDetailDO selectLatestRegisteredByGenerationTaskId(Long generationTaskId) {
        return selectOne(new LambdaQueryWrapperX<TkTiktokPublishDetailDO>()
                .eq(TkTiktokPublishDetailDO::getGenerationTaskId, generationTaskId)
                .isNotNull(TkTiktokPublishDetailDO::getPublishUrl)
                .ne(TkTiktokPublishDetailDO::getPublishUrl, "")
                .orderByDesc(TkTiktokPublishDetailDO::getPublishUrlRegisteredTime)
                .orderByDesc(TkTiktokPublishDetailDO::getId)
                .last("LIMIT 1"));
    }

    default List<TkTiktokPublishDetailDO> selectRegisteredByGenerationTaskIds(Collection<Long> generationTaskIds) {
        if (generationTaskIds == null || generationTaskIds.isEmpty()) {
            return Collections.emptyList();
        }
        return selectList(new LambdaQueryWrapperX<TkTiktokPublishDetailDO>()
                .in(TkTiktokPublishDetailDO::getGenerationTaskId, generationTaskIds)
                .isNotNull(TkTiktokPublishDetailDO::getPublishUrl)
                .ne(TkTiktokPublishDetailDO::getPublishUrl, "")
                .orderByDesc(TkTiktokPublishDetailDO::getPublishUrlRegisteredTime)
                .orderByDesc(TkTiktokPublishDetailDO::getId));
    }

    default List<TkTiktokPublishDetailDO> selectStaleProcessingList(LocalDateTime deadline, int limit) {
        return selectList(new LambdaQueryWrapperX<TkTiktokPublishDetailDO>()
                .eq(TkTiktokPublishDetailDO::getStatus, "PROCESSING")
                .isNotNull(TkTiktokPublishDetailDO::getPublishId)
                .and(wrapper -> wrapper
                        .isNull(TkTiktokPublishDetailDO::getLastSyncTime)
                        .or()
                        .le(TkTiktokPublishDetailDO::getLastSyncTime, deadline))
                .orderByAsc(TkTiktokPublishDetailDO::getLastSyncTime)
                .last("LIMIT " + Math.max(1, limit)));
    }

}


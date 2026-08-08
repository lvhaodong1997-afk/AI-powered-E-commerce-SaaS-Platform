package cn.iocoder.yudao.module.tk.dal.mysql;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.tk.controller.admin.generation.vo.TkGenerationBatchPageReqVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationBatchDO;
import cn.iocoder.yudao.module.tk.service.scope.TkUserScope;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TkGenerationBatchMapper extends BaseMapperX<TkGenerationBatchDO> {

    default PageResult<TkGenerationBatchDO> selectPage(TkGenerationBatchPageReqVO reqVO, TkUserScope scope) {
        return selectPage(reqVO, new LambdaQueryWrapperX<TkGenerationBatchDO>()
                .eqIfPresent(TkGenerationBatchDO::getTenantId, scope.isGlobalPlatformView() ? null : scope.getTenantId())
                .eqIfPresent(TkGenerationBatchDO::getCreator, scope.canReadAllTenantRecords() ? null : scope.getUserIdString())
                .eqIfPresent(TkGenerationBatchDO::getLibraryId, reqVO.getLibraryId())
                .eqIfPresent(TkGenerationBatchDO::getStatus, reqVO.getStatus())
                .likeIfPresent(TkGenerationBatchDO::getName, reqVO.getKeyword())
                .orderByDesc(TkGenerationBatchDO::getId));
    }
}

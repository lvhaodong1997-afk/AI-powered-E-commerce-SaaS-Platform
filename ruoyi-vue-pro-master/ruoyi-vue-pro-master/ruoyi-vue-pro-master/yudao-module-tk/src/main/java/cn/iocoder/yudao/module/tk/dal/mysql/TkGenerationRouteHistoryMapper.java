package cn.iocoder.yudao.module.tk.dal.mysql;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.tk.controller.admin.generation.route.vo.TkGenerationRouteHistoryPageReqVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationRouteHistoryDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TkGenerationRouteHistoryMapper extends BaseMapperX<TkGenerationRouteHistoryDO> {

    default PageResult<TkGenerationRouteHistoryDO> selectPage(TkGenerationRouteHistoryPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<TkGenerationRouteHistoryDO>()
                .eqIfPresent(TkGenerationRouteHistoryDO::getRouteId, reqVO.getRouteId())
                .eqIfPresent(TkGenerationRouteHistoryDO::getMaterialPurpose, reqVO.getMaterialPurpose())
                .eqIfPresent(TkGenerationRouteHistoryDO::getProductCategoryCode, reqVO.getProductCategoryCode())
                .eqIfPresent(TkGenerationRouteHistoryDO::getRouteCode, reqVO.getRouteCode())
                .orderByDesc(TkGenerationRouteHistoryDO::getRouteVersion)
                .orderByDesc(TkGenerationRouteHistoryDO::getCreateTime));
    }

}

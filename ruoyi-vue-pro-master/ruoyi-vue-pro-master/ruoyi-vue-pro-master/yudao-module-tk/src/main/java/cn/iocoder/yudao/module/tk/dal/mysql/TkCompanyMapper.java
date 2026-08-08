package cn.iocoder.yudao.module.tk.dal.mysql;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.tk.controller.admin.company.vo.TkCompanyPageReqVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkCompanyDO;
import cn.iocoder.yudao.module.tk.service.scope.TkUserScope;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

import static cn.iocoder.yudao.framework.common.enums.CommonStatusEnum.ENABLE;

@Mapper
public interface TkCompanyMapper extends BaseMapperX<TkCompanyDO> {

    default PageResult<TkCompanyDO> selectPage(PageParam pageParam) {
        return selectPage(pageParam, new LambdaQueryWrapperX<TkCompanyDO>()
                .orderByDesc(TkCompanyDO::getId));
    }

    default PageResult<TkCompanyDO> selectPage(TkCompanyPageReqVO reqVO, TkUserScope scope) {
        return selectPage(reqVO, new LambdaQueryWrapperX<TkCompanyDO>()
                .eqIfPresent(TkCompanyDO::getTenantId, scope.isGlobalPlatformView() ? null : scope.getTenantId())
                .eqIfPresent(TkCompanyDO::getId, scope.isPlatformAdmin() || scope.isTenantAdmin() ? null : scope.getCompanyId())
                .likeIfPresent(TkCompanyDO::getName, reqVO.getName())
                .eqIfPresent(TkCompanyDO::getStatus, reqVO.getStatus())
                .orderByDesc(TkCompanyDO::getId));
    }

    default List<TkCompanyDO> selectSimpleList() {
        return selectList(new LambdaQueryWrapperX<TkCompanyDO>()
                .orderByDesc(TkCompanyDO::getId));
    }

    default List<TkCompanyDO> selectSimpleList(TkUserScope scope) {
        return selectList(new LambdaQueryWrapperX<TkCompanyDO>()
                .eqIfPresent(TkCompanyDO::getTenantId, scope.isGlobalPlatformView() ? null : scope.getTenantId())
                .eq(TkCompanyDO::getStatus, ENABLE.getStatus())
                .eqIfPresent(TkCompanyDO::getId, scope.isPlatformAdmin() || scope.isTenantAdmin() ? null : scope.getCompanyId())
                .orderByDesc(TkCompanyDO::getId));
    }

}


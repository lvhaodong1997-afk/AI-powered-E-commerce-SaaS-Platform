package cn.iocoder.yudao.module.tk.dal.mysql;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo.TkTiktokAccountGroupPageReqVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkTiktokAccountGroupDO;
import cn.iocoder.yudao.module.tk.service.scope.TkUserScope;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TkTiktokAccountGroupMapper extends BaseMapperX<TkTiktokAccountGroupDO> {

    default PageResult<TkTiktokAccountGroupDO> selectPage(TkTiktokAccountGroupPageReqVO reqVO, TkUserScope scope) {
        return selectPage(reqVO, new LambdaQueryWrapperX<TkTiktokAccountGroupDO>()
                .eqIfPresent(TkTiktokAccountGroupDO::getTenantId, scope.isGlobalPlatformView() ? null : scope.getTenantId())
                .eqIfPresent(TkTiktokAccountGroupDO::getCompanyId, scope.isPlatformAdmin() ? reqVO.getCompanyId() : null)
                .eqIfPresent(TkTiktokAccountGroupDO::getStatus, reqVO.getStatus())
                .and(StrUtil.isNotBlank(reqVO.getKeyword()), wrapper -> wrapper
                        .like(TkTiktokAccountGroupDO::getName, reqVO.getKeyword())
                        .or()
                        .like(TkTiktokAccountGroupDO::getLabels, reqVO.getKeyword())
                        .or()
                        .like(TkTiktokAccountGroupDO::getScene, reqVO.getKeyword()))
                .orderByDesc(TkTiktokAccountGroupDO::getId));
    }

}


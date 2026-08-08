package cn.iocoder.yudao.module.tk.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkBgmAssetDO;
import cn.iocoder.yudao.module.tk.service.scope.TkUserScope;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface TkBgmAssetMapper extends BaseMapperX<TkBgmAssetDO> {

    default List<TkBgmAssetDO> selectAvailableList(TkUserScope scope) {
        LambdaQueryWrapperX<TkBgmAssetDO> query = new LambdaQueryWrapperX<TkBgmAssetDO>()
                .eq(TkBgmAssetDO::getStatus, 1);
        if (scope.isGlobalPlatformView()) {
            query.eq(TkBgmAssetDO::getSourceType, "SYSTEM");
        } else if (scope.canReadAllTenantRecords()) {
            query.and(wrapper -> wrapper
                    .eq(TkBgmAssetDO::getSourceType, "SYSTEM")
                    .or(userWrapper -> userWrapper
                            .eq(TkBgmAssetDO::getSourceType, "USER")
                            .eq(TkBgmAssetDO::getTenantId, scope.getTenantId())));
        } else {
            query.and(wrapper -> wrapper
                    .eq(TkBgmAssetDO::getSourceType, "SYSTEM")
                    .or(userWrapper -> userWrapper
                            .eq(TkBgmAssetDO::getSourceType, "USER")
                            .eq(TkBgmAssetDO::getTenantId, scope.getTenantId())
                            .eq(TkBgmAssetDO::getCompanyId, scope.getCompanyId())));
        }
        return selectList(query
                .orderByAsc(TkBgmAssetDO::getSourceType)
                .orderByDesc(TkBgmAssetDO::getId));
    }

    default List<TkBgmAssetDO> selectSystemAvailableList() {
        return selectList(new LambdaQueryWrapperX<TkBgmAssetDO>()
                .eq(TkBgmAssetDO::getSourceType, "SYSTEM")
                .eq(TkBgmAssetDO::getStatus, 1)
                .orderByAsc(TkBgmAssetDO::getStyle)
                .orderByDesc(TkBgmAssetDO::getId));
    }

}

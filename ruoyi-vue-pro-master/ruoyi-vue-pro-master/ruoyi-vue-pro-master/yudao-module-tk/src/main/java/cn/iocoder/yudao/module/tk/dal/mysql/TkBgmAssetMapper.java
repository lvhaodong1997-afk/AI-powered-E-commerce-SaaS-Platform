package cn.iocoder.yudao.module.tk.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkBgmAssetDO;
import cn.iocoder.yudao.module.tk.service.scope.TkUserScope;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface TkBgmAssetMapper extends BaseMapperX<TkBgmAssetDO> {

    default List<TkBgmAssetDO> selectAvailableList(TkUserScope scope) {
        QueryWrapper<TkBgmAssetDO> query = new QueryWrapper<TkBgmAssetDO>()
                .eq("status", 1);
        if (scope.isGlobalPlatformView()) {
            query.eq("source_type", "SYSTEM");
        } else if (scope.canReadAllTenantRecords()) {
            query.and(wrapper -> wrapper
                    .eq("source_type", "SYSTEM")
                    .or(userWrapper -> userWrapper
                            .eq("source_type", "USER")
                            .eq("tenant_id", scope.getTenantId())
                            .and(companyWrapper -> applyLegacyCompanyFilter(companyWrapper, scope))));
        } else {
            query.and(wrapper -> wrapper
                    .eq("source_type", "SYSTEM")
                    .or(userWrapper -> userWrapper
                            .eq("source_type", "USER")
                            .eq("tenant_id", scope.getTenantId())
                            .and(companyWrapper -> applyLegacyCompanyFilter(companyWrapper, scope))));
        }
        return selectList(query
                .orderByAsc("source_type")
                .orderByDesc("id"));
    }

    default List<TkBgmAssetDO> selectSystemAvailableList() {
        return selectList(new LambdaQueryWrapperX<TkBgmAssetDO>()
                .eq(TkBgmAssetDO::getSourceType, "SYSTEM")
                .eq(TkBgmAssetDO::getStatus, 1)
                .orderByAsc(TkBgmAssetDO::getStyle)
                .orderByDesc(TkBgmAssetDO::getId));
    }

    default QueryWrapper<TkBgmAssetDO> applyLegacyCompanyFilter(QueryWrapper<TkBgmAssetDO> wrapper, TkUserScope scope) {
        if (scope.getCompanyId() == null) {
            return wrapper.isNull("company_id");
        }
        return wrapper.eq("company_id", scope.getCompanyId()).or().isNull("company_id");
    }

}

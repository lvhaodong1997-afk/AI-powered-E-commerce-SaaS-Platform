package cn.iocoder.yudao.module.tk.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationRouteDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TkGenerationRouteMapper extends BaseMapperX<TkGenerationRouteDO> {

    default TkGenerationRouteDO selectEnabledRoute(Long tenantId, String materialPurpose, String productCategoryCode) {
        return selectOne(new LambdaQueryWrapperX<TkGenerationRouteDO>()
                .eq(TkGenerationRouteDO::getTenantId, tenantId)
                .eq(TkGenerationRouteDO::getMaterialPurpose, materialPurpose)
                .eq(TkGenerationRouteDO::getProductCategoryCode, productCategoryCode)
                .eq(TkGenerationRouteDO::getEnabled, true)
                .last("LIMIT 1"));
    }

}

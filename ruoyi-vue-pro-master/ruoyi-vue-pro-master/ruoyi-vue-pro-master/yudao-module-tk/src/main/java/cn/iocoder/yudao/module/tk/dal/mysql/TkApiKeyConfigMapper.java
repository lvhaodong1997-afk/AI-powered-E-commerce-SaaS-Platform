package cn.iocoder.yudao.module.tk.dal.mysql;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkApiKeyConfigDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TkApiKeyConfigMapper extends BaseMapperX<TkApiKeyConfigDO> {

    default TkApiKeyConfigDO selectEnabledByProviderAndKey(String provider, String configKey) {
        return selectOne(new LambdaQueryWrapperX<TkApiKeyConfigDO>()
                .eq(TkApiKeyConfigDO::getProvider, provider)
                .eq(TkApiKeyConfigDO::getConfigKey, configKey)
                .eq(TkApiKeyConfigDO::getStatus, CommonStatusEnum.ENABLE.getStatus()));
    }

}

package cn.iocoder.yudao.module.tk.service.config;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkApiKeyConfigDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkApiKeyConfigMapper;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;

@Service
@Validated
public class TkApiKeyConfigServiceImpl implements TkApiKeyConfigService {

    @Resource
    private TkApiKeyConfigMapper apiKeyConfigMapper;

    @Override
    public String getValue(String provider, String configKey) {
        TkApiKeyConfigDO config = apiKeyConfigMapper.selectEnabledByProviderAndKey(provider, configKey);
        return config == null ? null : config.getConfigValue();
    }

    @Override
    public String getValueOrDefault(String provider, String configKey, String defaultValue) {
        return StrUtil.blankToDefault(getValue(provider, configKey), defaultValue);
    }

}

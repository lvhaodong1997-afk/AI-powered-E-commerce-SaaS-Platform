package cn.iocoder.yudao.module.tk.service.config;

public interface TkApiKeyConfigService {

    String getValue(String provider, String configKey);

    String getValueOrDefault(String provider, String configKey, String defaultValue);

}

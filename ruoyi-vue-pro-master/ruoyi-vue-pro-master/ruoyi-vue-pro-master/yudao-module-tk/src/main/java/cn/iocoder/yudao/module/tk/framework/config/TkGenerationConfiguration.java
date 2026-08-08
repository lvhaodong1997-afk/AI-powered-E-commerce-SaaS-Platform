package cn.iocoder.yudao.module.tk.framework.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(TkGenerationProperties.class)
public class TkGenerationConfiguration {
}

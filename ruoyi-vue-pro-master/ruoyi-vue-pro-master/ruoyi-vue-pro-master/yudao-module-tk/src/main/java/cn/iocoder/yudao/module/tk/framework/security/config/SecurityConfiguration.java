package cn.iocoder.yudao.module.tk.framework.security.config;

import cn.iocoder.yudao.framework.security.config.AuthorizeRequestsCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;

@Configuration(proxyBeanMethods = false, value = "tkSecurityConfiguration")
public class SecurityConfiguration {

    @Bean("tkAuthorizeRequestsCustomizer")
    public AuthorizeRequestsCustomizer authorizeRequestsCustomizer() {
        return new AuthorizeRequestsCustomizer() {

            @Override
            public void customize(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry registry) {
                registry.requestMatchers(buildAdminApi("/tk/open/video/**")).permitAll()
                        .requestMatchers(buildAdminApi("/tk/open/copywriting/**")).permitAll()
                        .requestMatchers(buildAdminApi("/tk/open/v1/tiktok/**")).permitAll()
                        .requestMatchers("/tk/open/v1/tiktok/**").permitAll()
                        .requestMatchers("/tk/open/copywriting/**").permitAll()
                        .requestMatchers("/tk/open/video/**").permitAll();
            }

        };
    }

}

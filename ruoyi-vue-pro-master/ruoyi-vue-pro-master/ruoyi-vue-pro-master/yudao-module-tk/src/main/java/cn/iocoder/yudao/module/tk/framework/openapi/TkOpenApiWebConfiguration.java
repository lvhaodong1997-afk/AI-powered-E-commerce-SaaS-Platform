package cn.iocoder.yudao.module.tk.framework.openapi;

import cn.iocoder.yudao.framework.common.enums.WebFilterOrderEnum;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenApiRequestLogMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class TkOpenApiWebConfiguration {

    @Bean
    public FilterRegistrationBean<TkOpenApiAuthenticationFilter> tkOpenApiAuthenticationFilter(
            TkOpenApiAuthenticationService authenticationService,
            TkOpenApiRequestLogMapper requestLogMapper,
            @Value("${tk.open-api.max-signed-body-bytes:8388608}") int maxSignedBodyBytes) {
        FilterRegistrationBean<TkOpenApiAuthenticationFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new TkOpenApiAuthenticationFilter(authenticationService, requestLogMapper, maxSignedBodyBytes));
        bean.setOrder(WebFilterOrderEnum.REQUEST_BODY_CACHE_FILTER + 2);
        bean.addUrlPatterns("/*");
        return bean;
    }
}

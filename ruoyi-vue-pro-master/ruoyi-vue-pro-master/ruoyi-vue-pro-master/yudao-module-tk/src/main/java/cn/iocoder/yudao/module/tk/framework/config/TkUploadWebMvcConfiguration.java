package cn.iocoder.yudao.module.tk.framework.config;

import cn.iocoder.yudao.module.tk.service.upload.TkLocalUploadStorageService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

@Configuration
public class TkUploadWebMvcConfiguration implements WebMvcConfigurer {

    @Resource
    private TkLocalUploadStorageService localUploadStorageService;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = localUploadStorageService.getRootDir().toUri().toString();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(location);
    }

}

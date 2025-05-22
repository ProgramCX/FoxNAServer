package cn.programcx.foxnaserver.config;

import cn.programcx.foxnaserver.interceptor.DirectoryPermissionInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfiguration implements WebMvcConfigurer {
    @Autowired
    DirectoryPermissionInterceptor directoryPermissionInterceptor;


    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(directoryPermissionInterceptor)
                .addPathPatterns("/api/file/**");
    }
}

package dev.mark.api.config;

import dev.mark.api.interceptor.SetupInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final SetupInterceptor setupInterceptor;

    public WebMvcConfig(SetupInterceptor setupInterceptor) {
        this.setupInterceptor = setupInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(setupInterceptor).addPathPatterns("/**");
    }
}

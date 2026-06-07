package com.novick.points.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.novick.points.security.SessionAuthInterceptor;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final SessionAuthInterceptor sessionAuthInterceptor;

    public WebMvcConfig(SessionAuthInterceptor sessionAuthInterceptor) {
        this.sessionAuthInterceptor = sessionAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(sessionAuthInterceptor)
                .addPathPatterns("/admin/**", "/app/**", "/api/**")
                .excludePathPatterns("/", "/favicon.ico", "/app/login.html", "/admin/login.html", "/api/auth/**",
                        "/error", "/res/**", "/bg/**", "/include/**", "/webjars/**", "/css/**", "/js/**");
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/", "/app/login.html");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 上传图片访问：访问 http://IP:8080/upload/xxx.png 指向 upload/ 目录
        registry.addResourceHandler("/upload/**")
                .addResourceLocations("file:upload/", "file:./upload/");
    }
}

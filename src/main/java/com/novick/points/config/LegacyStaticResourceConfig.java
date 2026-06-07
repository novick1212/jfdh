package com.novick.points.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class LegacyStaticResourceConfig implements WebMvcConfigurer {

    private final AppProperties appProperties;

    public LegacyStaticResourceConfig(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String legacyPath = appProperties.getLegacyStaticPath();
        if (legacyPath == null || legacyPath.isBlank()) {
            return;
        }
        Path path = Paths.get(legacyPath);
        if (Files.exists(path)) {
            String location = path.toUri().toString();
            registry.addResourceHandler("/res/**", "/bg/**", "/include/**", "/error/**", "/webjars/**")
                    .addResourceLocations(location + "res/", location + "bg/", location + "include/",
                            location + "error/", location + "webjars/");
        }
    }
}

package com.aip.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

/**
 * 跨域配置
 * 支持通过配置指定允许的源
 */
@Configuration
public class CorsConfig {

    /** 允许的源（逗号分隔） */
    @Value("${cors.allowed-origins:}")
    private String allowedOrigins;

    /** 是否允许所有源（仅开发环境使用） */
    @Value("${cors.allow-all-origins:false}")
    private boolean allowAllOrigins;

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");

        if (allowAllOrigins) {
            // 仅开发环境使用，允许所有源
            config.addAllowedOriginPattern("*");
        } else if (StringUtils.hasText(allowedOrigins)) {
            // 生产环境：使用配置的允许源
            List<String> origins = Arrays.asList(allowedOrigins.split(","));
            origins.forEach(config::addAllowedOriginPattern);
        } else {
            // 默认：只允许本机（开发环境）
            config.addAllowedOriginPattern("http://localhost:*");
            config.addAllowedOriginPattern("http://127.0.0.1:*");
        }

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}

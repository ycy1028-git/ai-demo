package com.aip.common.security;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT配置属性
 * 支持从环境变量或配置文件读取
 */
@Slf4j
@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /** 密钥（支持环境变量 JWT_SECRET 覆盖） */
    @Value("${jwt.secret:${JWT_SECRET:}}")
    private String secret;

    /** 过期时间（毫秒），默认10分钟 */
    @Value("${jwt.expiration:600000}")
    private Long expiration;

    /** 请求头名称 */
    @Value("${jwt.header:Authorization}")
    private String header;

    /** Token前缀 */
    @Value("${jwt.prefix:Bearer}")
    private String prefix;

    /**
     * 验证密钥是否已配置
     */
    public boolean isSecretConfigured() {
        return secret != null && !secret.isBlank() && secret.length() >= 32;
    }

    /**
     * 获取有效的密钥
     * 如果未配置或太短，使用默认密钥（仅用于开发环境）
     */
    public String getEffectiveSecret() {
        if (!isSecretConfigured()) {
            log.warn("JWT密钥未配置或长度不足，建议在生产环境中设置 JWT_SECRET 环境变量");
            return "AiPlatformSecretKey2024ForJWTTokenGenerationMustBeLongEnough";
        }
        return secret;
    }
}

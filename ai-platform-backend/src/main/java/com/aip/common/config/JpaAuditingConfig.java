package com.aip.common.config;

import com.aip.common.security.LoginUser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * JPA 审计配置
 * 审计人信息获取逻辑：从 SecurityContext 中获取当前登录用户 ID
 * 注意: @EnableJpaAuditing 已配置在 AiPlatformApplication 主类
 */
@Configuration
public class JpaAuditingConfig {

    /**
     * 审计人信息提供者
     * 从 SecurityContextHolder 获取当前登录用户的 ID
     */
    @Bean
    public AuditorAware<String> auditorProvider() {
        return new SecurityContextAuditor();
    }

    /**
     * 安全上下文审计人实现
     */
    public static class SecurityContextAuditor implements AuditorAware<String> {

        @Override
        public Optional<String> getCurrentAuditor() {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                return Optional.empty();
            }

            Object principal = authentication.getPrincipal();
            if (principal instanceof LoginUser loginUser) {
                return Optional.ofNullable(loginUser.getUserId());
            }

            return Optional.empty();
        }
    }
}

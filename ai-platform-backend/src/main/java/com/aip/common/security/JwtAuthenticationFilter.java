package com.aip.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * JWT认证过滤器
 * 功能：
 * 1. 解析并验证 JWT Token
 * 2. 滑动过期：每次有效请求都刷新 Redis Key 的过期时间（10分钟）
 * 3. 性能优化：只刷新 Redis 过期时间，不重新生成 JWT
 */
@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader(jwtUtil.getJwtProperties().getHeader());
        String prefix = jwtUtil.getJwtProperties().getPrefix();

        if (authHeader != null && authHeader.startsWith(prefix + " ")) {
            String token = authHeader.substring(prefix.length() + 1).trim();

            if (!token.isEmpty() && jwtUtil.validateToken(token)) {
                String userId = jwtUtil.getUserId(token);
                String username = jwtUtil.getUsername(token);

                LoginUser loginUser = new LoginUser(userId, username);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(loginUser, null, Collections.emptyList());

                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.debug("用户 {} 认证成功", username);

                // 滑动过期：只刷新 Redis Key 的过期时间，不重新生成 JWT
                jwtUtil.slidingRefresh(token);
            }
        }

        filterChain.doFilter(request, response);
    }
}

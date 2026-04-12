package com.aip.common.security;

import cn.hutool.core.util.StrUtil;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * JWT工具类
 * 采用双重过期机制：
 * 1. JWT Token：24小时过期，技术保障
 * 2. Redis Key：10分钟滑动过期，业务保障
 */
@Slf4j
@Component
public class JwtUtil {

    private static final String TOKEN_KEY = "jwt:token:";
    /** JWT Token 本身的过期时间（毫秒），默认24小时 */
    private static final long JWT_EXPIRATION = 24 * 60 * 60 * 1000L;
    /** Redis Key 的滑动过期时间（毫秒），默认1小时 */
    private static final long SLIDING_EXPIRATION = 60 * 60 * 1000L;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    private final Map<String, String> localTokenStore = new ConcurrentHashMap<>();

    /**
     * 检查 Redis 是否可用
     */
    private boolean isRedisAvailable() {
        return redisTemplate != null;
    }

    /**
     * 获取JWT配置属性
     */
    public JwtProperties getJwtProperties() {
        return jwtProperties;
    }

    /**
     * 获取滑动过期时间（毫秒）
     */
    public long getSlidingExpiration() {
        return SLIDING_EXPIRATION;
    }

    /**
     * 创建Token
     * @param userId 用户ID（UUIDv7 无横杠字符串）
     * @param username 用户名
     */
    public String createToken(String userId, String username) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);

        SecretKey key = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));

        // JWT 本身使用较长过期时间（24小时）
        String token = Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(new java.util.Date())
                .setExpiration(new java.util.Date(System.currentTimeMillis() + JWT_EXPIRATION))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        String tokenKey = TOKEN_KEY + userId;

        // Redis 中存储的 key 使用滑动过期时间（10分钟）
        if (isRedisAvailable()) {
            try {
                redisTemplate.opsForValue().set(tokenKey, token, SLIDING_EXPIRATION, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                log.warn("Redis存储Token失败，使用本地存储: {}", e.getMessage());
                localTokenStore.put(tokenKey, token);
            }
        } else {
            localTokenStore.put(tokenKey, token);
        }

        return token;
    }

    /**
     * 解析Token
     */
    public Claims parseToken(String token) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
            return Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (JwtException e) {
            log.error("Token解析失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 验证Token是否有效
     */
    public boolean validateToken(String token) {
        if (StrUtil.isBlank(token)) {
            return false;
        }

        Claims claims = parseToken(token);
        if (claims == null) {
            return false;
        }

        String userIdStr = claims.get("userId", String.class);
        String tokenKey = TOKEN_KEY + userIdStr;

        if (isRedisAvailable()) {
            try {
                Object storedToken = redisTemplate.opsForValue().get(tokenKey);
                return token.equals(storedToken);
            } catch (Exception e) {
                log.warn("Redis验证Token失败，使用本地存储验证: {}", e.getMessage());
                return token.equals(localTokenStore.get(tokenKey));
            }
        } else {
            return token.equals(localTokenStore.get(tokenKey));
        }
    }

    /**
     * 获取用户ID（UUIDv7 无横杠字符串）
     */
    public String getUserId(String token) {
        Claims claims = parseToken(token);
        if (claims == null) {
            return null;
        }
        return claims.get("userId", String.class);
    }

    /**
     * 获取用户名
     */
    public String getUsername(String token) {
        Claims claims = parseToken(token);
        return claims != null ? claims.get("username", String.class) : null;
    }

    /**
     * 移除Token（退出登录）
     */
    public void removeToken(String token) {
        String userId = getUserId(token);
        if (userId != null) {
            String tokenKey = TOKEN_KEY + userId;

            if (isRedisAvailable()) {
                try {
                    redisTemplate.delete(tokenKey);
                } catch (Exception e) {
                    log.warn("Redis删除Token失败: {}", e.getMessage());
                }
            }
            localTokenStore.remove(tokenKey);
        }
    }

    /**
     * 滑动刷新Redis Key的过期时间（不重新生成JWT）
     * 这是核心优化：只刷新Redis过期时间，不重新生成JWT
     * @param token 要刷新的token
     * @return 是否刷新成功
     */
    public boolean slidingRefresh(String token) {
        String userId = getUserId(token);
        if (userId == null) {
            return false;
        }

        String tokenKey = TOKEN_KEY + userId;

        if (isRedisAvailable()) {
            try {
                // 检查 token 是否匹配
                Object storedToken = redisTemplate.opsForValue().get(tokenKey);
                if (token.equals(storedToken)) {
                    // 只刷新 Redis key 的过期时间，不重新生成 JWT
                    redisTemplate.expire(tokenKey, SLIDING_EXPIRATION, TimeUnit.MILLISECONDS);
                    log.debug("用户 {} 的 Redis Token 已滑动刷新过期时间", getUsername(token));
                    return true;
                } else {
                    // Token 不匹配，可能已被顶替
                    log.warn("Token 不匹配，可能存在并发登录: {}", userId);
                    return false;
                }
            } catch (Exception e) {
                log.warn("Redis滑动刷新Token失败: {}", e.getMessage());
                return false;
            }
        } else {
            // 无 Redis 时，使用本地存储（本地存储无法滑动刷新，保持原样）
            log.debug("无 Redis 模式，跳过滑动刷新");
            return true;
        }
    }

    /**
     * 获取Token在Redis中的剩余时间（毫秒）
     */
    public long getRedisRemainingTime(String token) {
        String userId = getUserId(token);
        if (userId == null) {
            return 0;
        }

        String tokenKey = TOKEN_KEY + userId;

        if (isRedisAvailable()) {
            try {
                Long ttl = redisTemplate.getExpire(tokenKey, TimeUnit.MILLISECONDS);
                return ttl != null && ttl > 0 ? ttl : 0;
            } catch (Exception e) {
                log.warn("获取Redis剩余时间失败: {}", e.getMessage());
                return 0;
            }
        }
        return 0;
    }
}

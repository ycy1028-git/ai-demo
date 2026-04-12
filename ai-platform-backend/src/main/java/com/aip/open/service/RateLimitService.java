package com.aip.open.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 流量控制服务
 * 基于Redis实现QPS限制和额度控制
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final StringRedisTemplate redisTemplate;

    private static final String RATE_LIMIT_PREFIX = "open_api:ratelimit:";
    private static final String QUOTA_PREFIX = "open_api:quota:";

    /**
     * 检查QPS是否允许通过
     *
     * @param credentialId 凭证ID
     * @param qpsLimit     QPS限制
     * @return true=允许，false=超限
     */
    public boolean checkQps(String credentialId, int qpsLimit) {
        if (qpsLimit <= 0) {
            return true; // 0或负数表示不限制
        }

        String key = RATE_LIMIT_PREFIX + credentialId;
        Long current = redisTemplate.opsForValue().increment(key);

        if (current != null && current == 1) {
            // 首次访问，设置过期时间
            redisTemplate.expire(key, 1, TimeUnit.SECONDS);
        }

        if (current != null && current > qpsLimit) {
            log.warn("QPS超限: credentialId={}, limit={}, current={}", credentialId, qpsLimit, current);
            return false;
        }

        return true;
    }

    /**
     * 获取当前QPS使用数
     */
    public long getCurrentQps(String credentialId) {
        String key = RATE_LIMIT_PREFIX + credentialId;
        String value = redisTemplate.opsForValue().get(key);
        return value != null ? Long.parseLong(value) : 0;
    }

    /**
     * 检查每日额度
     *
     * @param credentialId 凭证ID
     * @param dailyLimit   每日限额（-1=不限）
     * @param currentUsage 当前使用量
     * @return true=允许，false=超限
     */
    public boolean checkDailyQuota(String credentialId, long dailyLimit, long currentUsage) {
        if (dailyLimit < 0) {
            return true; // -1表示不限
        }
        return currentUsage < dailyLimit;
    }

    /**
     * 检查每月额度
     *
     * @param credentialId 凭证ID
     * @param monthlyLimit 每月限额（-1=不限）
     * @param currentUsage 当前使用量
     * @return true=允许，false=超限
     */
    public boolean checkMonthlyQuota(String credentialId, long monthlyLimit, long currentUsage) {
        if (monthlyLimit < 0) {
            return true; // -1表示不限
        }
        return currentUsage < monthlyLimit;
    }

    /**
     * 获取剩余每日额度
     */
    public long getRemainingDailyQuota(String credentialId, long dailyLimit, long currentUsage) {
        if (dailyLimit < 0) {
            return -1; // 不限
        }
        return Math.max(0, dailyLimit - currentUsage);
    }

    /**
     * 获取剩余每月额度
     */
    public long getRemainingMonthlyQuota(String credentialId, long monthlyLimit, long currentUsage) {
        if (monthlyLimit < 0) {
            return -1; // 不限
        }
        return Math.max(0, monthlyLimit - currentUsage);
    }

    /**
     * 限流结果
     */
    public static class RateLimitResult {
        private final boolean allowed;
        private final String code;
        private final String message;
        private final Long remainingQuota;

        public RateLimitResult(boolean allowed, String code, String message, Long remainingQuota) {
            this.allowed = allowed;
            this.code = code;
            this.message = message;
            this.remainingQuota = remainingQuota;
        }

        public static RateLimitResult allow(Long remainingQuota) {
            return new RateLimitResult(true, null, null, remainingQuota);
        }

        public static RateLimitResult denyQps() {
            return new RateLimitResult(false, "RATE_LIMIT_EXCEEDED", "请求过于频繁，请稍后重试", null);
        }

        public static RateLimitResult denyDaily() {
            return new RateLimitResult(false, "DAILY_QUOTA_EXCEEDED", "今日额度已用尽，请明日再试", null);
        }

        public static RateLimitResult denyMonthly() {
            return new RateLimitResult(false, "MONTHLY_QUOTA_EXCEEDED", "本月额度已用尽", null);
        }

        public boolean isAllowed() {
            return allowed;
        }

        public String getCode() {
            return code;
        }

        public String getMessage() {
            return message;
        }

        public Long getRemainingQuota() {
            return remainingQuota;
        }
    }

    /**
     * 全面流量检查
     */
    public RateLimitResult checkAll(String credentialId, int qpsLimit, long dailyLimit, long currentDailyUsage,
                                    long monthlyLimit, long currentMonthlyUsage) {
        // 1. 检查QPS
        if (!checkQps(credentialId, qpsLimit)) {
            return RateLimitResult.denyQps();
        }

        // 2. 检查每日额度
        if (!checkDailyQuota(credentialId, dailyLimit, currentDailyUsage)) {
            return RateLimitResult.denyDaily();
        }

        // 3. 检查每月额度
        if (!checkMonthlyQuota(credentialId, monthlyLimit, currentMonthlyUsage)) {
            return RateLimitResult.denyMonthly();
        }

        // 4. 计算剩余额度（取每日和每月中较小的）
        long remaining = -1;
        if (dailyLimit > 0 && monthlyLimit > 0) {
            remaining = Math.min(dailyLimit - currentDailyUsage, monthlyLimit - currentMonthlyUsage);
        } else if (dailyLimit > 0) {
            remaining = dailyLimit - currentDailyUsage;
        } else if (monthlyLimit > 0) {
            remaining = monthlyLimit - currentMonthlyUsage;
        }

        return RateLimitResult.allow(remaining);
    }
}

package com.aip.flow.service.impl;

import com.aip.flow.engine.FlowContext;
import com.aip.flow.service.IContextManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 上下文管理器实现
 * 负责 Redis 中对话上下文的读写和过期管理
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContextManager implements IContextManager {

    private static final String CONTEXT_KEY_PREFIX = "ai:flow:context:";
    private static final long EXPIRE_SECONDS = 1800; // 30分钟

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void saveContext(FlowContext context) {
        if (context == null || context.getUserId() == null) {
            return;
        }
        try {
            String key = CONTEXT_KEY_PREFIX + context.getUserId();
            redisTemplate.opsForValue().set(key, context, EXPIRE_SECONDS, TimeUnit.SECONDS);
            log.debug("上下文已保存: userId={}, sessionId={}", context.getUserId(), context.getSessionId());
        } catch (Exception e) {
            log.error("保存上下文失败: userId={}, error={}", context.getUserId(), e.getMessage());
        }
    }

    @Override
    public FlowContext getContext(String userId) {
        if (userId == null) {
            return null;
        }
        try {
            String key = CONTEXT_KEY_PREFIX + userId;
            Object value = redisTemplate.opsForValue().get(key);
            if (value instanceof FlowContext) {
                log.debug("上下文已加载: userId={}, sessionId={}", userId, ((FlowContext) value).getSessionId());
                return (FlowContext) value;
            }
        } catch (Exception e) {
            log.error("加载上下文失败: userId={}, error={}", userId, e.getMessage());
        }
        return null;
    }

    @Override
    public void clearContext(String userId) {
        if (userId == null) {
            return;
        }
        try {
            String key = CONTEXT_KEY_PREFIX + userId;
            redisTemplate.delete(key);
            log.debug("上下文已清除: userId={}", userId);
        } catch (Exception e) {
            log.error("清除上下文失败: userId={}, error={}", userId, e.getMessage());
        }
    }

    @Override
    public void refreshExpire(String userId) {
        if (userId == null) {
            return;
        }
        try {
            String key = CONTEXT_KEY_PREFIX + userId;
            redisTemplate.expire(key, EXPIRE_SECONDS, TimeUnit.SECONDS);
            log.debug("上下文过期时间已刷新: userId={}", userId);
        } catch (Exception e) {
            log.error("刷新上下文过期时间失败: userId={}, error={}", userId, e.getMessage());
        }
    }
}

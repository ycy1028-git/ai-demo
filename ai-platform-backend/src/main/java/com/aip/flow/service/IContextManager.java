package com.aip.flow.service;

import com.aip.flow.engine.FlowContext;

/**
 * 上下文管理器接口
 */
public interface IContextManager {

    /**
     * 保存上下文到 Redis
     */
    void saveContext(FlowContext context);

    /**
     * 从 Redis 加载上下文
     */
    FlowContext getContext(String userId);

    /**
     * 清除上下文
     */
    void clearContext(String userId);

    /**
     * 刷新过期时间
     */
    void refreshExpire(String userId);
}

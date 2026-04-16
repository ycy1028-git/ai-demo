package com.aip.flow.service;

import com.aip.flow.engine.FlowContext;
import com.aip.flow.dto.IntentType;
import com.aip.flow.dto.IntentRouteResult;

/**
 * 路由策略接口
 */
public interface RoutingStrategy {
    
    /**
     * 执行路由
     * @param userMessage 用户消息
     * @param context 流程上下文
     * @return 路由结果
     */
    IntentRouteResult route(String userMessage, FlowContext context);
    
    /**
     * 获取策略名称
     */
    String getName();
    
    /**
     * 获取策略置信度
     */
    double getConfidence();
    
    /**
     * 获取策略描述
     */
    String getDescription();
}

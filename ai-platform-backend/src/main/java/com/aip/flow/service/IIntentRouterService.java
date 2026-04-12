package com.aip.flow.service;

import com.aip.flow.dto.IntentRouteResult;
import com.aip.flow.engine.FlowContext;

/**
 * 意图路由服务接口
 * 负责分析用户意图，决定路由类型
 */
public interface IIntentRouterService {

    /**
     * 执行意图路由
     * @param userMessage 用户消息
     * @param context 流程上下文（可为空，用于多轮对话）
     * @return 意图路由结果
     */
    IntentRouteResult route(String userMessage, FlowContext context);

    /**
     * 快速匹配模板
     * @param userMessage 用户消息
     * @return 匹配的模板编码，无匹配返回 null
     */
    String quickMatchTemplate(String userMessage);
}

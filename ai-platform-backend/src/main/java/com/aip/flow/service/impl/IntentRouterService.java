package com.aip.flow.service.impl;

import com.aip.flow.dto.IntentRouteResult;
import com.aip.flow.engine.FlowContext;
import com.aip.flow.service.HybridRoutingDecisionMaker;
import com.aip.flow.service.IIntentRouterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 优化的意图路由服务
 * 集成混合路由策略，提升路由准确性和性能
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntentRouterService implements IIntentRouterService {

    private final HybridRoutingDecisionMaker hybridRoutingDecisionMaker;

    /**
     * 执行意图路由（主入口方法）
     * @param userMessage 用户消息
     * @param context 流程上下文
     * @return 意图路由结果
     */
    @Override
    public IntentRouteResult route(String userMessage, FlowContext context) {
        try {
            log.info("开始意图路由: userMessage={}", userMessage);
            
            // 使用混合路由决策器
            IntentRouteResult result = hybridRoutingDecisionMaker.route(userMessage, context);
            
            log.info("意图路由完成: routeType={}, confidence={}, requiresKnowledge={}", 
                result.getRouteType(), result.getConfidence(), result.isRequiresKnowledgeRetrieval());
            
            return result;
            
        } catch (Exception e) {
            log.error("意图路由异常: userMessage={}, error={}", userMessage, e.getMessage(), e);
            return buildFallbackRoute(userMessage);
        }
    }

    /**
     * 快速匹配模板（保持向后兼容）
     * @param userMessage 用户消息
     * @return 匹配的模板编码
     */
    @Override
    public String quickMatchTemplate(String userMessage) {
        // 这里可以保留原有的快速匹配逻辑
        // 或者直接返回null，让混合路由处理
        return null;
    }

    /**
     * 构建兜底路由结果
     */
    private IntentRouteResult buildFallbackRoute(String userMessage) {
        log.warn("使用兜底路由方案");
        
        return IntentRouteResult.builder()
            .routeType(IntentRouteResult.RouteType.DYNAMIC_PLAN)
            .confidence(0.2)
            .requiresKnowledgeRetrieval(true)
            .routeReason("兜底路由：主路由异常")
            .build();
    }
}
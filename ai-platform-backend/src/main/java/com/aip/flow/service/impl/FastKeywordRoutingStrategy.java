package com.aip.flow.service.impl;

import com.aip.flow.engine.FlowContext;
import com.aip.flow.dto.IntentType;
import com.aip.flow.dto.IntentRouteResult;
import com.aip.flow.service.RoutingStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 快速关键词路由策略
 * 基于关键词匹配进行快速路由，性能最优
 */
@Slf4j
@Component
public class FastKeywordRoutingStrategy implements RoutingStrategy {

    @Override
    public IntentRouteResult route(String userMessage, FlowContext context) {
        try {
            // 快速判断是否需要知识检索
            boolean requiresKnowledge = requiresKnowledgeRetrieval(userMessage);
            
            // 快速判断是否是操作执行
            boolean isActionExecution = isActionExecution(userMessage);
            
            if (requiresKnowledge && !isActionExecution) {
                return IntentRouteResult.builder()
                    .routeType(IntentRouteResult.RouteType.DIRECT_ANSWER)
                    .confidence(0.8)
                    .requiresKnowledgeRetrieval(true)
                    .routeReason("快速路由：知识查询类型")
                    .build();
            }
            
            if (isActionExecution) {
                return IntentRouteResult.builder()
                    .routeType(IntentRouteResult.RouteType.DYNAMIC_PLAN)
                    .confidence(0.7)
                    .requiresKnowledgeRetrieval(false)
                    .routeReason("快速路由：操作执行类型")
                    .build();
            }
            
            // 默认返回直接回答
            return IntentRouteResult.builder()
                .routeType(IntentRouteResult.RouteType.DIRECT_ANSWER)
                .confidence(0.5)
                .requiresKnowledgeRetrieval(false)
                .routeReason("快速路由：默认类型")
                .build();
                
        } catch (Exception e) {
            log.warn("快速路由失败: userMessage={}, error={}", userMessage, e.getMessage());
            return null;
        }
    }

    @Override
    public String getName() {
        return "FAST_KEYWORD";
    }

    @Override
    public double getConfidence() {
        return 0.7; // 快速路由的置信度
    }

    @Override
    public String getDescription() {
        return "基于关键词的快速路由策略，性能最优";
    }

    /**
     * 判断是否需要知识检索
     */
    private boolean requiresKnowledgeRetrieval(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return false;
        }

        String normalized = userMessage.toLowerCase().trim();
        
        // 知识查询特征词
        String[] knowledgeFeatures = {
            "制度", "规定", "政策", "手册", "文档", "流程", "说明", 
            "怎么", "如何", "为什么", "是什么", "能否", "是否", 
            "区别", "作用", "原理", "配置", "报错", "接口", "参数"
        };
        
        for (String feature : knowledgeFeatures) {
            if (normalized.contains(feature)) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * 判断是否是操作执行
     */
    private boolean isActionExecution(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return false;
        }

        String normalized = userMessage.toLowerCase().trim();
        
        // 操作执行特征词
        String[] actionFeatures = {
            "提交", "创建", "发起", "审批", "报销", "请假", "工单", 
            "新增", "删除", "修改", "更新", "办理", "申请", "执行"
        };
        
        for (String feature : actionFeatures) {
            if (normalized.contains(feature)) {
                return true;
            }
        }
        
        return false;
    }
}
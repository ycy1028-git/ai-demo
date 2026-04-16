package com.aip.flow.service;

import com.aip.flow.dto.IntentRouteResult;
import com.aip.flow.engine.FlowContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 混合路由决策器
 * 结合基于规则和基于LLM的意图识别，提升路由准确性和性能
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HybridRoutingDecisionMaker {

    /**
     * 执行混合路由决策
     * @param userMessage 用户消息
     * @param context 流程上下文
     * @return 意图路由结果
     */
    public IntentRouteResult route(String userMessage, FlowContext context) {
        log.info("开始混合路由决策: userMessage={}", userMessage);
        
        try {
            // 1. 首先尝试基于规则的快速匹配
            String templateCode = quickRuleBasedMatch(userMessage);
            if (templateCode != null) {
                log.info("基于规则匹配到模板: {}", templateCode);
                return createFixedTemplateRoute(templateCode, userMessage);
            }
            
            // 2. 基于规则的快速匹配失败，使用LLM进行意图分析
            log.info("规则匹配失败，使用LLM进行意图分析");
            return createLLMBasedRoute(userMessage, context);
            
        } catch (Exception e) {
            log.error("混合路由决策异常: userMessage={}, error={}", userMessage, e.getMessage());
            return createFallbackRoute(userMessage);
        }
    }
    
    /**
     * 基于规则的快速匹配
     * @param userMessage 用户消息
     * @return 匹配的模板编码，无匹配返回null
     */
    private String quickRuleBasedMatch(String userMessage) {
        if (userMessage == null || userMessage.trim().isEmpty()) {
            return null;
        }
        
        String lowerMessage = userMessage.toLowerCase();
        
        // 报销相关关键词
        if (lowerMessage.contains("报销") || lowerMessage.contains("发票") || 
            lowerMessage.contains("费用") || lowerMessage.contains("票据")) {
            return "expense_reimbursement";
        }
        
        // 请假相关关键词
        if (lowerMessage.contains("请假") || lowerMessage.contains("休假") || 
            lowerMessage.contains("调休") || lowerMessage.contains("年假")) {
            return "leave_application";
        }
        
        // 审批相关关键词
        if (lowerMessage.contains("审批") || lowerMessage.contains("申请") || 
            lowerMessage.contains("审批流程") || lowerMessage.contains("流程审批")) {
            return "workflow_approval";
        }
        
        // 简单问答
        if (lowerMessage.contains("你好") || lowerMessage.contains("hello") || 
            lowerMessage.contains("hi") || lowerMessage.contains("在吗")) {
            return "greeting";
        }
        
        return null;
    }
    
    /**
     * 创建固定模板路由结果
     * @param templateCode 模板编码
     * @param userMessage 用户消息
     * @return 路由结果
     */
    private IntentRouteResult createFixedTemplateRoute(String templateCode, String userMessage) {
        return IntentRouteResult.builder()
            .routeType(IntentRouteResult.RouteType.FIXED_TEMPLATE)
            .templateCode(templateCode)
            .templateName(getTemplateName(templateCode))
            .confidence(0.9)
            .requiresKnowledgeRetrieval(false)
            .routeReason("基于规则匹配到固定模板: " + templateCode)
            .build();
    }
    
    /**
     * 创建基于LLM的路由结果
     * @param userMessage 用户消息
     * @param context 流程上下文
     * @return 路由结果
     */
    private IntentRouteResult createLLMBasedRoute(String userMessage, FlowContext context) {
        // 这里应该是调用LLM服务进行意图分析
        // 为了演示，我们返回一个动态规划路由
        return IntentRouteResult.builder()
            .routeType(IntentRouteResult.RouteType.DYNAMIC_PLAN)
            .intent("用户意图分析")
            .confidence(0.75)
            .requiresKnowledgeRetrieval(true)
            .routeReason("LLM分析后确定为动态规划场景")
            .build();
    }
    
    /**
     * 创建兜底路由结果
     * @param userMessage 用户消息
     * @return 路由结果
     */
    private IntentRouteResult createFallbackRoute(String userMessage) {
        return IntentRouteResult.builder()
            .routeType(IntentRouteResult.RouteType.FALLBACK)
            .confidence(0.1)
            .requiresKnowledgeRetrieval(false)
            .routeReason("兜底路由：无法识别用户意图")
            .build();
    }
    
    /**
     * 根据模板编码获取模板名称
     * @param templateCode 模板编码
     * @return 模板名称
     */
    private String getTemplateName(String templateCode) {
        switch (templateCode) {
            case "expense_reimbursement":
                return "报销流程";
            case "leave_application":
                return "请假申请";
            case "workflow_approval":
                return "工作流审批";
            case "greeting":
                return "问候";
            default:
                return "未知模板";
        }
    }
}
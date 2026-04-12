package com.aip.flow.dto;

import lombok.Data;
import lombok.Builder;
import java.util.Map;

/**
 * 意图路由结果
 * <p>
 * 由 IntentRouterService 调用 LLM 分析后返回，包含：
 * - 路由类型（固定模板/动态规划/直接回答/兜底）
 * - 匹配的模板信息
 * - LLM 抽取的业务参数
 * - 置信度和追问提示
 * <p>
 * 使用场景：
 * 1. FlowEngine 根据 routeType 决定执行方式
 * 2. 当 confidence < 0.7 时，需要追问用户补充信息
 * 3. params 中包含 LLM 从用户消息中抽取的参数
 */
@Data
@Builder
public class IntentRouteResult {

    /** 路由类型，决定后续执行方式 */
    private RouteType routeType;

    /** 匹配到的模板编码（固定模板时有效） */
    private String templateCode;

    /** 匹配到的模板名称（用于日志和调试） */
    private String templateName;

    /** LLM 识别出的用户意图描述 */
    private String intent;

    /** LLM 从用户消息中抽取的业务参数（如订单号、金额等） */
    private Map<String, Object> params;

    /** 置信度：0.0~1.0，低于 0.7 需要追问用户 */
    private Double confidence;

    /** 当置信度低时，LLM 建议的追问内容 */
    private String prompt;

    /** 是否需要更多用户输入 */
    private boolean needMoreInput;

    /** 建议的下一步操作（可选） */
    private String nextAction;

    /**
     * 路由类型枚举
     * <p>
     * - FIXED_TEMPLATE: 复杂业务场景，使用预设的固定模板流程
     * - DYNAMIC_PLAN: 简单场景，LLM 自动选择节点组合
     * - DIRECT_ANSWER: 简单问答，无需复杂流程
     * - FALLBACK: 无法匹配时的兜底处理
     */
    public enum RouteType {
        /** 固定模板 - 复杂业务场景，使用预设流程（如报销、请假） */
        FIXED_TEMPLATE,
        
        /** 动态规划 - 简单场景，LLM 自动选择节点 */
        DYNAMIC_PLAN,
        
        /** 直接回答 - 简单问答，无需流程 */
        DIRECT_ANSWER,
        
        /** 兜底 - 无法匹配时的默认处理 */
        FALLBACK
    }

    /**
     * 是否为固定模板路由
     */
    public boolean isFixedTemplate() {
        return RouteType.FIXED_TEMPLATE == routeType;
    }

    /**
     * 是否为动态规划路由
     */
    public boolean isDynamicPlan() {
        return RouteType.DYNAMIC_PLAN == routeType;
    }

    /**
     * 是否为直接回答
     */
    public boolean isDirectAnswer() {
        return RouteType.DIRECT_ANSWER == routeType;
    }

    /**
     * 是否为兜底
     */
    public boolean isFallback() {
        return RouteType.FALLBACK == routeType;
    }
}

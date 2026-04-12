package com.aip.flow.service.impl;

import com.aip.ai.entity.AiModelConfig;
import com.aip.ai.service.IAiModelConfigService;
import com.aip.common.ai.UnifiedLlmService;
import com.aip.common.ai.UnifiedLlmService.Message;
import com.aip.flow.dto.NodeExecutionPlan;
import com.aip.flow.dto.NodeExecutionPlan.PlannedNode;
import com.aip.flow.dto.RegisteredNode;
import com.aip.flow.engine.FlowContext;
import com.aip.flow.service.IDynamicPlannerService;
import com.aip.flow.service.INodeRegistryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 动态规划服务实现
 * <p>
 * 核心职责：
 * 当没有匹配到固定模板时，根据用户意图动态生成节点执行计划
 * <p>
 * 与固定模板的区别：
 * - 固定模板：人工预设流程，适用于复杂、固定的业务场景
 * - 动态规划：LLM 自动选择节点组合，适用于简单、多变的业务场景
 * <p>
 * 规划策略：
 * - Eager（预规划）：一次性生成完整路径，执行效率高
 * - Lazy（按需规划）：每步动态判断，更灵活但调用次数多
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DynamicPlannerService implements IDynamicPlannerService {

    /** 节点注册服务，用于获取可用节点列表 */
    private final INodeRegistryService nodeRegistryService;

    /** AI 模型配置服务 */
    private final IAiModelConfigService aiModelConfigService;

    /** 统一 LLM 调用服务 */
    private final UnifiedLlmService unifiedLlmService;

    /** JSON 解析器 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 动态规划的最大节点数限制，防止生成过多节点 */
    private static final int MAX_NODES = 5;

    /**
     * 生成节点执行计划（入口方法）
     * <p>
     * 流程：
     * 1. 构建规划提示词（包含用户问题、上下文、可用节点、规划原则）
     * 2. 调用 LLM 获取节点组合
     * 3. 解析 LLM 返回，生成 NodeExecutionPlan
     *
     * @param userMessage 用户消息
     * @param context 流程上下文
     * @return 节点执行计划
     */
    @Override
    public NodeExecutionPlan plan(String userMessage, FlowContext context) {
        try {
            log.info("开始动态规划: userMessage={}", userMessage);

            // ========== 1. 构建规划提示词 ==========
            String prompt = buildPlanningPrompt(userMessage, context);

            // ========== 2. 调用 LLM 获取规划 ==========
            AiModelConfig modelConfig = aiModelConfigService.getDefaultModel();
            List<Message> messages = List.of(new Message("user", prompt));

            String response = unifiedLlmService.chat(modelConfig, messages);

            // ========== 3. 解析规划结果 ==========
            return parsePlanningResult(response);

        } catch (Exception e) {
            log.error("动态规划异常: error={}", e.getMessage(), e);
            // 异常时返回默认规划
            return buildDefaultPlan();
        }
    }

    /**
     * 判断是否需要动态规划
     * <p>
     * 简单判断逻辑：
     * - 包含问号 → 需要动态规划
     * - 问候语（你好、您好等）→ 不需要，直接回答
     * - 其他 → 需要动态规划
     */
    @Override
    public boolean shouldDynamicPlan(String userMessage) {
        // ========== 明确需要 AI 回答的问题 ==========
        if (userMessage.contains("?") || userMessage.contains("？")) {
            return true;
        }

        // ========== 问候语等简单场景，不需要动态规划 ==========
        List<String> simplePatterns = Arrays.asList(
                "你好", "您好", "嗨", "hi", "hello", "在吗", "在不在"
        );

        for (String pattern : simplePatterns) {
            if (userMessage.toLowerCase().contains(pattern.toLowerCase())) {
                return false;
            }
        }

        // 其他场景默认需要动态规划
        return true;
    }

    /**
     * 构建动态规划提示词
     * <p>
     * 提示词包含：
     * 1. 用户问题
     * 2. 已收集的参数（避免重复收集）
     * 3. 可用节点列表
     * 4. 规划原则（指导 LLM 如何选择节点）
     * 5. 输出格式要求
     */
    private String buildPlanningPrompt(String userMessage, FlowContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 动态节点规划任务\n\n");
        sb.append("根据用户问题，从可用节点中选择最优的节点组合。\n\n");

        // ========== 用户问题 ==========
        sb.append("## 用户问题\n");
        sb.append(userMessage).append("\n\n");

        // ========== 已收集的参数（避免重复收集） ==========
        if (context != null && context.getParams() != null && !context.getParams().isEmpty()) {
            sb.append("## 已收集参数\n");
            context.getParams().forEach((k, v) -> {
                // 跳过系统参数（下划线开头）
                if (!k.startsWith("_")) {
                    sb.append(String.format("- %s: %s\n", k, v));
                }
            });
            sb.append("\n");
        }

        // ========== 可用节点列表 ==========
        List<RegisteredNode> nodes = nodeRegistryService.getAllNodes();
        sb.append("## 可用节点\n");
        sb.append("| 节点类型 | 功能 | 适用场景 |\n");
        sb.append("|---------|------|----------|\n");

        for (RegisteredNode node : nodes) {
            sb.append(String.format("| %s | %s | %s |\n",
                    node.getCode(),
                    node.getName(),
                    node.getDescription()));
        }
        sb.append("\n");

        // ========== 规划原则 ==========
        sb.append("## 规划原则\n");
        sb.append("1. 优先收集必要参数\n");
        sb.append("2. 需要业务数据时调用 execute 节点\n");
        sb.append("3. 询问政策/制度时先检索知识库\n");
        sb.append("4. 最终都需要 LLM 生成回答\n");
        sb.append("5. 控制节点数量（不超过 ").append(MAX_NODES).append(" 个）\n\n");

        // ========== 输出格式 ==========
        sb.append("## 输出格式\n");
        sb.append("""
            {
              "planId": "plan_001",
              "strategy": "eager",
              "reason": "规划理由说明",
              "confidence": 0.8,
              "nodes": [
                {"nodeType": "knowledge_retrieval", "order": 1, "reason": "需要检索相关知识", "required": true},
                {"nodeType": "llm_call", "order": 2, "reason": "基于检索结果生成回答", "required": true}
              ]
            }

            注意：nodes 为空时表示无法规划，应直接使用 LLM 回答
            """);

        return sb.toString();
    }

    /**
     * 解析 LLM 返回的规划结果
     */
    private NodeExecutionPlan parsePlanningResult(String response) {
        try {
            // 提取 JSON 字符串
            String jsonStr = extractJson(response);
            if (jsonStr == null) {
                log.warn("无法解析规划结果 JSON");
                return buildDefaultPlan();
            }

            JsonNode jsonNode = objectMapper.readTree(jsonStr);

            // ========== 解析基本信息 ==========
            String planId = jsonNode.has("planId") ? jsonNode.get("planId").asText() 
                    : "plan_" + System.currentTimeMillis();
            String strategy = jsonNode.has("strategy") ? jsonNode.get("strategy").asText() : "eager";
            String reason = jsonNode.has("reason") && !jsonNode.get("reason").isNull() 
                    ? jsonNode.get("reason").asText() : "";
            double confidence = jsonNode.has("confidence") ? jsonNode.get("confidence").asDouble() : 0.5;

            // ========== 解析节点列表 ==========
            List<PlannedNode> nodes = new ArrayList<>();
            if (jsonNode.has("nodes") && jsonNode.get("nodes").isArray()) {
                int order = 1;
                for (JsonNode node : jsonNode.get("nodes")) {
                    PlannedNode plannedNode = PlannedNode.builder()
                            .nodeType(node.has("nodeType") ? node.get("nodeType").asText() : "")
                            .nodeName(node.has("nodeName") && !node.get("nodeName").isNull() 
                                    ? node.get("nodeName").asText() : "")
                            .order(node.has("order") ? node.get("order").asInt() : order++)
                            .reason(node.has("reason") && !node.get("reason").isNull() 
                                    ? node.get("reason").asText() : "")
                            .required(node.has("required") ? node.get("required").asBoolean() : true)
                            .build();
                    nodes.add(plannedNode);
                }
            }

            log.info("动态规划成功: planId={}, nodeCount={}", planId, nodes.size());

            return NodeExecutionPlan.builder()
                    .planId(planId)
                    .strategy(strategy)
                    .reason(reason)
                    .confidence(confidence)
                    .nodes(nodes)
                    .build();

        } catch (Exception e) {
            log.error("解析规划结果失败: {}", e.getMessage());
            return buildDefaultPlan();
        }
    }

    /**
     * 构建默认规划
     * <p>
     * 当 LLM 规划失败时使用：
     * - 知识检索节点（非必需，可能无相关知识）
     * - LLM 调用节点（必需，生成最终回答）
     */
    private NodeExecutionPlan buildDefaultPlan() {
        return NodeExecutionPlan.builder()
                .planId("default_" + System.currentTimeMillis())
                .strategy("eager")
                .reason("默认规划")
                .confidence(0.5)
                .nodes(Arrays.asList(
                        PlannedNode.builder()
                                .nodeType("knowledge_retrieval")
                                .order(1)
                                .reason("检索相关知识")
                                .required(false)  // 非必需，知识库可能没有相关内容
                                .build(),
                        PlannedNode.builder()
                                .nodeType("llm_call")
                                .order(2)
                                .reason("生成回答")
                                .required(true)   // 必需，必须有回答
                                .build()
                ))
                .build();
    }

    /**
     * 从 LLM 响应中提取 JSON 字符串
     */
    private String extractJson(String response) {
        int start = response.indexOf("{");
        int end = response.lastIndexOf("}");
        if (start >= 0 && end > start) {
            return response.substring(start, end + 1);
        }
        return null;
    }
}

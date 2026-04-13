package com.aip.flow.service.impl;

import com.aip.flow.dto.IntentRouteResult;
import com.aip.flow.dto.NodeExecutionPlan;
import com.aip.flow.dto.NodeExecutionPlan.PlannedNode;
import com.aip.flow.engine.FlowContext;
import com.aip.flow.engine.FlowDefinition;
import com.aip.flow.engine.NodeResult;
import com.aip.flow.entity.FlowTemplate;
import com.aip.flow.executor.LlmCallExecutor;
import com.aip.flow.executor.NodeExecutor;
import com.aip.flow.mapper.FlowTemplateMapper;
import com.aip.flow.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.function.Consumer;

/**
 * 流程引擎实现
 * <p>
 * 核心职责：
 * 1. 接收用户消息，协调各服务完成意图识别、路由决策
 * 2. 根据路由类型执行固定模板或动态规划
 * 3. 调度节点执行器完成业务流程
 * 4. 管理对话上下文，支持多轮对话
 * <p>
 * 执行流程：
 * 1. 加载/初始化上下文（从 Redis）
 * 2. 执行意图路由（LLM 分析 + 模板匹配）
 * 3. 根据路由类型执行：
 *    - 固定模板：按预设节点顺序执行
 *    - 动态规划：LLM 生成节点组合执行
 * 4. 返回结果给前端
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowEngine implements IFlowEngine {

    /** 上下文管理器，负责 Redis 中的对话状态存取 */
    private final IContextManager contextManager;

    /** 意图路由服务，负责分析用户意图和路由决策 */
    private final IIntentRouterService intentRouterService;

    /** 动态规划服务，当无固定模板时生成节点执行计划 */
    private final IDynamicPlannerService dynamicPlannerService;

    /** 节点注册服务，提供节点执行器查找能力 */
    private final INodeRegistryService nodeRegistryService;

    /** 流程模板 Mapper，用于查询固定模板 */
    private final FlowTemplateMapper flowTemplateMapper;

    /** JSON 解析器，用于解析流程定义 */
    private final ObjectMapper objectMapper;

    /** 节点执行最大重试次数，防止无限重试 */
    private static final int MAX_RETRY_COUNT = 3;

    /**
     * 执行对话流程（入口方法）
     * <p>
     * 完整流程：
     * 1. 加载上下文（Redis）
     * 2. 无上下文 → 初始化 + 意图路由 + 执行
     * 3. 有上下文 → 更新状态 + 继续执行
     *
     * @param userId 用户ID
     * @param userMessage 用户消息
     * @return 节点执行结果（包含 AI 回复、是否需要追问等）
     */
    @Override
    public NodeResult execute(String userId, String userMessage) {
        try {
            FlowContext context = contextManager.getContext(userId);
            NodeResult result;

            if (context == null) {
                context = initContext(userId, userMessage);
                result = routeAndExecute(context, userMessage);
            } else {
                contextManager.refreshExpire(userId);

                if ("waiting".equals(context.getStatus())) {
                    context.setCurrentMessage(userMessage);
                    context.getParams().put("_current_message", userMessage);
                    result = continueExecution(context);
                } else {
                    prepareNewTurn(context, userMessage);
                    result = routeAndExecute(context, userMessage);
                }
            }

            appendConversationTurn(context, userMessage, result);
            contextManager.saveContext(context);
            return result;

        } catch (Exception e) {
            log.error("流程执行异常: userId={}, error={}", userId, e.getMessage(), e);
            return handleFlowError(e);
        }
    }

    /**
     * 流式执行（内部调用 execute，结果通过回调返回）
     */
    @Override
    public NodeResult executeStreaming(String userId, String userMessage, Consumer<String> onChunk) {
        try {
            FlowContext context = contextManager.getContext(userId);
            NodeResult result;

            if (context == null) {
                context = initContext(userId, userMessage);
                result = routeAndExecuteStreaming(context, userMessage, onChunk);
            } else {
                contextManager.refreshExpire(userId);

                if ("waiting".equals(context.getStatus())) {
                    context.setCurrentMessage(userMessage);
                    context.getParams().put("_current_message", userMessage);
                    result = continueExecutionStreaming(context, onChunk);
                } else {
                    prepareNewTurn(context, userMessage);
                    result = routeAndExecuteStreaming(context, userMessage, onChunk);
                }
            }

            appendConversationTurn(context, userMessage, result);
            contextManager.saveContext(context);
            return result;

        } catch (Exception e) {
            log.error("流程流式执行异常: userId={}, error={}", userId, e.getMessage(), e);
            onChunk.accept("抱歉，系统处理时遇到问题，请稍后再试。");
            return handleFlowError(e);
        }
    }

    private NodeResult routeAndExecute(FlowContext context, String userMessage) {
        IntentRouteResult routeResult = intentRouterService.route(userMessage, context);

        context.setCurrentMessage(userMessage);
        context.setTemplateCode(routeResult.getTemplateCode());
        context.setStatus(routeResult.isNeedMoreInput() ? "waiting" : "running");

        if (routeResult.getParams() != null) {
            context.getParams().putAll(routeResult.getParams());
        }
        context.getParams().put("_current_message", userMessage);

        if (routeResult.isNeedMoreInput()) {
            return NodeResult.needInput(routeResult.getPrompt());
        }

        return executeByRouteType(routeResult, context);
    }

    private NodeResult routeAndExecuteStreaming(FlowContext context, String userMessage, Consumer<String> onChunk) {
        IntentRouteResult routeResult = intentRouterService.route(userMessage, context);

        context.setCurrentMessage(userMessage);
        context.setTemplateCode(routeResult.getTemplateCode());
        context.setStatus(routeResult.isNeedMoreInput() ? "waiting" : "running");

        if (routeResult.getParams() != null) {
            context.getParams().putAll(routeResult.getParams());
        }
        context.getParams().put("_current_message", userMessage);

        if (routeResult.isNeedMoreInput()) {
            return NodeResult.needInput(routeResult.getPrompt());
        }

        return executeByRouteTypeStreaming(routeResult, context, onChunk);
    }

    private void prepareNewTurn(FlowContext context, String userMessage) {
        context.setCurrentNodeIndex(0);
        context.setTemplateCode(null);
        context.setStatus("running");
        context.setCurrentMessage(userMessage);

        context.getParams().clear();
        context.getParams().put("_current_message", userMessage);
        context.getMetadata().clear();
    }

    private void appendConversationTurn(FlowContext context, String userMessage, NodeResult result) {
        if (context == null || userMessage == null || userMessage.isBlank()) {
            return;
        }

        context.addUserMessage(userMessage);

        if (result == null) {
            return;
        }

        String assistantMessage = null;
        if (result.isNeedMoreInput() && result.getPrompt() != null && !result.getPrompt().isBlank()) {
            assistantMessage = result.getPrompt();
        } else if (result.getOutput() != null && !result.getOutput().isBlank()
                && !"流程已结束".equals(result.getOutput())) {
            assistantMessage = result.getOutput();
        } else if (!result.isSuccess() && result.getUserMessage() != null && !result.getUserMessage().isBlank()) {
            assistantMessage = result.getUserMessage();
        }

        if (assistantMessage != null) {
            context.addAssistantMessage(assistantMessage);
        }
    }

    /**
     * 根据路由类型流式执行
     */
    private NodeResult executeByRouteTypeStreaming(IntentRouteResult routeResult, FlowContext context, Consumer<String> onChunk) {
        return switch (routeResult.getRouteType()) {
            case FIXED_TEMPLATE -> executeFixedTemplateStreaming(routeResult.getTemplateCode(), context, onChunk);
            case DYNAMIC_PLAN -> executeDynamicPlanStreaming(context, onChunk);
            case DIRECT_ANSWER -> routeResult.isRequiresKnowledgeRetrieval()
                    ? executeKnowledgeThenLlmStreaming(context, onChunk)
                    : executeDirectAnswerStreaming(context, onChunk);
            case FALLBACK -> routeResult.isRequiresKnowledgeRetrieval()
                    ? executeKnowledgeThenLlmStreaming(context, onChunk)
                    : executeFallbackStreaming(context, onChunk);
        };
    }

    /**
     * 执行固定模板流程（流式）
     */
    private NodeResult executeFixedTemplateStreaming(String templateCode, FlowContext context, Consumer<String> onChunk) {
        log.info("执行固定模板(流式): templateCode={}", templateCode);

        FlowTemplate template = flowTemplateMapper
                .findByTemplateCodeAndStatusAndDeletedFalse(templateCode, 1)
                .orElse(null);

        if (template == null) {
            return executeDynamicPlanStreaming(context, onChunk);
        }

        FlowDefinition definition = parseFlowDefinition(template.getFlowData());
        if (definition == null || definition.getNodes() == null || definition.getNodes().isEmpty()) {
            return executeDynamicPlanStreaming(context, onChunk);
        }

        return executeFlowRecursiveStreaming(context, definition, 0, onChunk);
    }

    /**
     * 执行动态规划流程（流式）
     */
    private NodeResult executeDynamicPlanStreaming(FlowContext context, Consumer<String> onChunk) {
        log.info("执行动态规划(流式)");
        return executeDefaultLLMCallStreaming(context, onChunk);
    }

    /**
     * 执行直接回答（流式）
     */
    private NodeResult executeDirectAnswerStreaming(FlowContext context, Consumer<String> onChunk) {
        log.info("执行直接回答(流式)");
        return executeDefaultLLMCallStreaming(context, onChunk);
    }

    /**
     * 执行兜底处理（流式）
     */
    private NodeResult executeFallbackStreaming(FlowContext context, Consumer<String> onChunk) {
        log.info("执行兜底(流式)");
        String templateCode = context.getTemplateCode();
        if (templateCode != null) {
            return executeFixedTemplateStreaming(templateCode, context, onChunk);
        }
        return executeDynamicPlanStreaming(context, onChunk);
    }

    /**
     * 继续执行多轮对话（流式）
     */
    private NodeResult continueExecutionStreaming(FlowContext context, Consumer<String> onChunk) {
        String templateCode = context.getTemplateCode();
        if (templateCode != null) {
            return executeFixedTemplateStreaming(templateCode, context, onChunk);
        }
        return executeDynamicPlanStreaming(context, onChunk);
    }

    /**
     * 递归执行固定模板节点（流式）
     */
    private NodeResult executeFlowRecursiveStreaming(FlowContext context, FlowDefinition definition, int retryCount, Consumer<String> onChunk) {
        while (context.getCurrentNodeIndex() < definition.getNodes().size()) {
            FlowDefinition.FlowNode node = definition.getNodeByIndex(context.getCurrentNodeIndex());

            if (node == null) {
                context.setCurrentNodeIndex(context.getCurrentNodeIndex() + 1);
                continue;
            }

            log.info("执行节点(流式): nodeId={}, nodeType={}", node.getId(), node.getType());

            NodeExecutor executor = nodeRegistryService.getExecutor(node.getType());
            if (executor == null) {
                log.warn("未找到执行器，跳过节点: nodeType={}", node.getType());
                context.setCurrentNodeIndex(context.getCurrentNodeIndex() + 1);
                continue;
            }

            try {
                NodeResult result;

                // 如果是 LLM 节点且支持流式
                if ("llm_call".equals(node.getType()) && executor instanceof LlmCallExecutor) {
                    LlmCallExecutor llmExecutor = (LlmCallExecutor) executor;
                    result = llmExecutor.executeStreaming(context, node.getData(), onChunk);
                } else {
                    result = executor.execute(context, node.getData());
                    if (result.getOutput() != null) {
                        onChunk.accept(result.getOutput());
                    }
                }

                result = processNodeResult(context, definition, result);

                if (result.isNeedMoreInput() || "completed".equals(result.getStatus())) {
                    return result;
                }

                if (!result.isSuccess() && retryCount < MAX_RETRY_COUNT) {
                    return executeFlowRecursiveStreaming(context, definition, retryCount + 1, onChunk);
                }

                if (!result.isSuccess()) {
                    return handleNodeError(context, node, result);
                }

                if (result.getParams() != null) {
                    context.getParams().putAll(result.getParams());
                }

                if (result.getParams() != null && result.getParams().containsKey("_next_node")) {
                    String nextNodeId = (String) result.getParams().get("_next_node");
                    int nextIndex = definition.findNodeIndexById(nextNodeId);
                    if (nextIndex >= 0) {
                        context.setCurrentNodeIndex(nextIndex);
                    }
                }

                contextManager.saveContext(context);

            } catch (Exception e) {
                log.error("节点执行异常(流式): nodeId={}, error={}", node.getId(), e.getMessage());
                if (retryCount < MAX_RETRY_COUNT) {
                    return executeFlowRecursiveStreaming(context, definition, retryCount + 1, onChunk);
                }
                return handleNodeError(context, node, NodeResult.fail("节点执行失败", "NODE_ERROR"));
            }
        }

        context.setStatus("completed");
        contextManager.saveContext(context);

        return NodeResult.completed();
    }

    /**
     * 执行默认的 LLM 调用（流式）
     */
    private NodeResult executeDefaultLLMCallStreaming(FlowContext context, Consumer<String> onChunk) {
        log.info("执行默认 LLM 调用(流式)");

        NodeExecutor executor = nodeRegistryService.getExecutor("llm_call");
        if (executor == null) {
            onChunk.accept("抱歉，系统暂时无法处理您的问题。");
            return NodeResult.fail("系统暂时无法处理您的问题", "NO_EXECUTOR");
        }

        try {
            Map<String, Object> config = new HashMap<>();
            config.put("systemPrompt", buildDefaultPromptWithKnowledge(context, null));

            NodeResult result;
            if (executor instanceof LlmCallExecutor) {
                result = ((LlmCallExecutor) executor).executeStreaming(context, config, onChunk);
            } else {
                result = executor.execute(context, config);
                if (result.getOutput() != null) {
                    onChunk.accept(result.getOutput());
                }
            }

            context.setStatus("completed");
            contextManager.saveContext(context);

            return result;

        } catch (Exception e) {
            log.error("默认 LLM 调用异常(流式): {}", e.getMessage());
            onChunk.accept("抱歉，AI 服务暂时繁忙，请稍后再试。");
            return NodeResult.fail("AI 服务暂时繁忙，请稍后再试", "LLM_ERROR");
        }
    }

    /**
     * 根据路由类型执行不同的处理逻辑
     *
     * @param routeResult 路由结果
     * @param context 流程上下文
     * @return 节点执行结果
     */
    private NodeResult executeByRouteType(IntentRouteResult routeResult, FlowContext context) {
        return switch (routeResult.getRouteType()) {
            // 固定模板：复杂业务场景，使用预设的节点流程
            case FIXED_TEMPLATE -> executeFixedTemplate(routeResult.getTemplateCode(), context);
            // 动态规划：简单场景，LLM 自动选择节点组合
            case DYNAMIC_PLAN -> executeDynamicPlan(context);
            // 直接回答：简单问答，无需复杂流程
            case DIRECT_ANSWER -> routeResult.isRequiresKnowledgeRetrieval()
                    ? executeKnowledgeThenLlm(context)
                    : executeDirectAnswer(context);
            // 兜底：无法匹配时的默认处理
            case FALLBACK -> routeResult.isRequiresKnowledgeRetrieval()
                    ? executeKnowledgeThenLlm(context)
                    : executeFallback(context);
        };
    }

    /**
     * 执行固定模板流程
     * <p>
     * 流程：
     * 1. 从数据库查询模板
     * 2. 解析流程定义（nodes + edges）
     * 3. 递归执行每个节点
     *
     * @param templateCode 模板编码
     * @param context 流程上下文
     * @return 节点执行结果
     */
    private NodeResult executeFixedTemplate(String templateCode, FlowContext context) {
        log.info("执行固定模板: templateCode={}", templateCode);

        // 查询模板
        FlowTemplate template = flowTemplateMapper
                .findByTemplateCodeAndStatusAndDeletedFalse(templateCode, 1)
                .orElse(null);

        // 模板不存在或未发布，降级到动态规划
        if (template == null) {
            log.warn("未找到模板或模板未发布: {}", templateCode);
            return executeDynamicPlan(context);
        }

        // 解析流程定义
        FlowDefinition definition = parseFlowDefinition(template.getFlowData());
        if (definition == null || definition.getNodes() == null || definition.getNodes().isEmpty()) {
            log.warn("模板节点为空: {}", templateCode);
            return executeDynamicPlan(context);
        }

        // 递归执行节点
        return executeFlowRecursive(context, definition, 0);
    }

    /**
     * 执行动态规划流程
     * <p>
     * 流程：
     * 1. 调用 LLM 生成节点执行计划
     * 2. 按计划顺序执行节点
     * 3. 失败时降级到默认 LLM 调用
     *
     * @param context 流程上下文
     * @return 节点执行结果
     */
    private NodeResult executeDynamicPlan(FlowContext context) {
        log.info("执行动态规划");

        // 生成节点执行计划
        NodeExecutionPlan plan = dynamicPlannerService.plan(context.getCurrentMessage(), context);
        if (plan == null || plan.isEmpty()) {
            log.warn("动态规划返回空计划");
            return executeDefaultLLMCall(context);
        }

        // 保存规划信息到上下文
        context.setMetadata("execution_plan", plan);
        context.setMetadata("plan_strategy", plan.getStrategy());

        // 按计划执行节点
        return executePlanNodes(context, plan);
    }

    /**
     * 执行直接回答（简单问答）
     */
    private NodeResult executeDirectAnswer(FlowContext context) {
        log.info("执行直接回答");
        return executeDefaultLLMCall(context);
    }

    /**
     * 执行兜底处理
     * 优先尝试使用已设置的模板，否则降级到动态规划
     */
    private NodeResult executeFallback(FlowContext context) {
        log.info("执行兜底");
        String templateCode = context.getTemplateCode();
        if (templateCode != null) {
            return executeFixedTemplate(templateCode, context);
        }
        return executeDynamicPlan(context);
    }

    /**
     * 继续执行多轮对话
     */
    private NodeResult continueExecution(FlowContext context) {
        String templateCode = context.getTemplateCode();

        // 如果有模板，继续执行固定模板
        if (templateCode != null) {
            return executeFixedTemplate(templateCode, context);
        }

        // 无模板，执行动态规划
        return executeDynamicPlan(context);
    }

    /**
     * 递归执行固定模板节点
     * <p>
     * 核心循环：
     * 1. 获取当前节点
     * 2. 获取节点执行器
     * 3. 执行节点
     * 4. 处理结果（成功/需要输入/失败）
     * 5. 更新节点索引，继续下一节点
     *
     * @param context 流程上下文
     * @param definition 流程定义
     * @param retryCount 当前重试次数
     * @return 节点执行结果
     */
    private NodeResult executeFlowRecursive(FlowContext context, FlowDefinition definition, int retryCount) {
        // 遍历所有节点
        while (context.getCurrentNodeIndex() < definition.getNodes().size()) {
            FlowDefinition.FlowNode node = definition.getNodeByIndex(context.getCurrentNodeIndex());

            // 跳过空节点
            if (node == null) {
                context.setCurrentNodeIndex(context.getCurrentNodeIndex() + 1);
                continue;
            }

            log.info("执行节点: nodeId={}, nodeType={}", node.getId(), node.getType());

            // 获取节点执行器
            NodeExecutor executor = nodeRegistryService.getExecutor(node.getType());
            if (executor == null) {
                log.warn("未找到执行器，跳过节点: nodeType={}", node.getType());
                context.setCurrentNodeIndex(context.getCurrentNodeIndex() + 1);
                continue;
            }

            try {
                // 执行节点
                NodeResult result = executor.execute(context, node.getData());
                result = processNodeResult(context, definition, result);

                // 需要更多输入，暂停流程
                if (result.isNeedMoreInput() || "completed".equals(result.getStatus())) {
                    return result;
                }

                // 执行失败且可重试
                if (!result.isSuccess() && retryCount < MAX_RETRY_COUNT) {
                    return executeFlowRecursive(context, definition, retryCount + 1);
                }

                // 执行失败，达到最大重试次数
                if (!result.isSuccess()) {
                    return handleNodeError(context, node, result);
                }

                // 更新上下文参数
                if (result.getParams() != null) {
                    context.getParams().putAll(result.getParams());
                }

                // 检查节点跳转指令
                if (result.getParams() != null && result.getParams().containsKey("_next_node")) {
                    String nextNodeId = (String) result.getParams().get("_next_node");
                    int nextIndex = definition.findNodeIndexById(nextNodeId);
                    if (nextIndex >= 0) {
                        context.setCurrentNodeIndex(nextIndex);
                    }
                }

                // 保存上下文
                contextManager.saveContext(context);

            } catch (Exception e) {
                log.error("节点执行异常: nodeId={}, error={}", node.getId(), e.getMessage());
                if (retryCount < MAX_RETRY_COUNT) {
                    return executeFlowRecursive(context, definition, retryCount + 1);
                }
                return handleNodeError(context, node, 
                        NodeResult.fail("节点执行失败", "NODE_ERROR"));
            }
        }

        // 所有节点执行完毕
        context.setStatus("completed");
        contextManager.saveContext(context);

        return NodeResult.completed();
    }

    /**
     * 执行动态规划的节点列表
     *
     * @param context 流程上下文
     * @param plan 节点执行计划
     * @return 最后一个节点的结果
     */
    private NodeResult executePlanNodes(FlowContext context, NodeExecutionPlan plan) {
        List<PlannedNode> nodes = plan.getNodes();
        if (nodes == null || nodes.isEmpty()) {
            return executeDefaultLLMCall(context);
        }

        NodeResult lastResult = null;

        // 按顺序执行每个节点
        for (PlannedNode plannedNode : nodes) {
            String nodeType = plannedNode.getNodeType();
            log.info("执行规划节点: nodeType={}, reason={}", nodeType, plannedNode.getReason());

            NodeExecutor executor = nodeRegistryService.getExecutor(nodeType);
            if (executor == null) {
                log.warn("未找到执行器: nodeType={}", nodeType);
                // 必需节点找不到，返回失败
                if (plannedNode.isRequired()) {
                    return NodeResult.fail("节点不支持: " + nodeType, "EXECUTOR_NOT_FOUND");
                }
                continue;
            }

            try {
                NodeResult result = executor.execute(context, plannedNode.getConfig());
                lastResult = result != null ? result : NodeResult.skip("节点返回空结果");

                // 需要更多输入
                if (result.isNeedMoreInput()) {
                    context.setStatus("waiting");
                    contextManager.saveContext(context);
                    return result;
                }

                // 必需节点执行失败
                if (!result.isSuccess() && plannedNode.isRequired()) {
                    return result;
                }

                // 更新上下文参数
                if (result.getParams() != null) {
                    context.getParams().putAll(result.getParams());
                }

                contextManager.saveContext(context);

            } catch (Exception e) {
                log.error("规划节点执行异常: nodeType={}, error={}", nodeType, e.getMessage());
                if (plannedNode.isRequired()) {
                    return NodeResult.fail("处理失败", "NODE_EXECUTION_ERROR");
                }
            }
        }

        // 流程完成
        context.setStatus("completed");
        contextManager.saveContext(context);

        return lastResult != null ? lastResult : NodeResult.completed();
    }

    /**
     * 处理节点执行结果
     * <p>
     * 决策逻辑：
     * - 需要输入：暂停流程，等待用户补充
     * - 执行失败：返回失败结果
     * - 执行成功：继续下一节点
     */
    private NodeResult processNodeResult(FlowContext context, FlowDefinition definition, NodeResult result) {
        if (result == null) {
            return NodeResult.skip("节点执行结果为空");
        }

        if (result.isNeedMoreInput()) {
            context.setStatus("waiting");
            contextManager.saveContext(context);
            return result;
        }

        if (!result.isSuccess()) {
            return result;
        }

        // 成功后进入下一节点
        context.setCurrentNodeIndex(context.getCurrentNodeIndex() + 1);
        return result;
    }

    /**
     * 处理节点执行错误
     * <p>
     * 根据节点类型提供友好的错误提示：
     * - LLM调用：提示 AI 服务繁忙
     * - 知识检索：跳过，继续流程
     * - 其他节点：跳过并提示
     */
    private NodeResult handleNodeError(FlowContext context, FlowDefinition.FlowNode node, NodeResult result) {
        String nodeType = node.getType();

        // LLM 调用失败
        if ("llm_call".equals(nodeType)) {
            return NodeResult.fail("AI 服务暂时繁忙，请稍后再试", "LLM_ERROR");
        }

        // 知识检索失败，跳过继续
        if ("knowledge_retrieval".equals(nodeType)) {
            context.setCurrentNodeIndex(context.getCurrentNodeIndex() + 1);
            return NodeResult.skip("未找到相关信息");
        }

        // 其他节点失败，跳过
        log.warn("节点执行失败，跳过继续: nodeType={}", nodeType);
        context.setCurrentNodeIndex(context.getCurrentNodeIndex() + 1);
        return NodeResult.skip("该步骤执行遇到问题，已为您跳过");
    }

    /**
     * 执行默认的 LLM 调用（兜底方案）
     * <p>
     * 当无固定模板或动态规划失败时使用
     */
    private NodeResult executeDefaultLLMCall(FlowContext context) {
        log.info("执行默认 LLM 调用");

        // 获取 LLM 执行器
        NodeExecutor executor = nodeRegistryService.getExecutor("llm_call");
        if (executor == null) {
            return NodeResult.fail("系统暂时无法处理您的问题", "NO_EXECUTOR");
        }

        try {
            // 构建默认配置
            Map<String, Object> config = new HashMap<>();
            config.put("systemPrompt", buildDefaultPromptWithKnowledge(context, null));

            // 执行 LLM 调用
            NodeResult result = executor.execute(context, config);

            // 流程结束
            context.setStatus("completed");
            contextManager.saveContext(context);

            return result;

        } catch (Exception e) {
            log.error("默认 LLM 调用异常: {}", e.getMessage());
            return NodeResult.fail("AI 服务暂时繁忙，请稍后再试", "LLM_ERROR");
        }
    }

    /**
     * 问答路径：先知识检索，再调用 LLM（非流式）
     */
    private NodeResult executeKnowledgeThenLlm(FlowContext context) {
        executeKnowledgeRetrievalForAnswer(context);
        return executeDefaultLLMCall(context);
    }

    /**
     * 问答路径：先知识检索，再调用 LLM（流式）
     */
    private NodeResult executeKnowledgeThenLlmStreaming(FlowContext context, Consumer<String> onChunk) {
        executeKnowledgeRetrievalForAnswer(context);
        return executeDefaultLLMCallStreaming(context, onChunk);
    }

    /**
     * 执行知识检索，并把检索结果写入上下文参数
     */
    private void executeKnowledgeRetrievalForAnswer(FlowContext context) {
        NodeExecutor knowledgeExecutor = nodeRegistryService.getExecutor("knowledge_retrieval");
        if (knowledgeExecutor == null) {
            return;
        }

        try {
            Map<String, Object> config = new HashMap<>();
            config.put("keyword", context.getCurrentMessage());
            config.put("searchType", "vector");
            config.put("topK", 5);

            NodeResult retrievalResult = knowledgeExecutor.execute(context, config);
            if (retrievalResult != null && retrievalResult.getParams() != null) {
                context.getParams().putAll(retrievalResult.getParams());
            }
        } catch (Exception e) {
            log.debug("知识检索执行失败，降级为直接回答: {}", e.getMessage());
        }
    }

    /**
     * 构建默认系统提示词，优先注入知识检索摘要
     */
    private String buildDefaultPromptWithKnowledge(FlowContext context, NodeResult retrievalResult) {
        String basePrompt = "你是一个友好的AI助手，请根据用户的问题给出有帮助的回答。";

        String knowledgeSummary = null;
        if (context != null && context.getParams() != null && context.getParams().get("knowledge_summary") != null) {
            knowledgeSummary = String.valueOf(context.getParams().get("knowledge_summary"));
        } else if (retrievalResult != null && retrievalResult.getOutput() != null) {
            knowledgeSummary = retrievalResult.getOutput();
        }

        if (knowledgeSummary == null || knowledgeSummary.isBlank()) {
            return basePrompt;
        }

        return basePrompt + "\n\n请优先依据下面检索到的知识回答；若知识不足以支撑结论，请明确说明不确定性：\n"
                + knowledgeSummary;
    }

    /**
     * 处理流程级别的错误
     * <p>
     * 将异常转换为用户友好的错误提示
     */
    private NodeResult handleFlowError(Exception e) {
        String errorType = e.getClass().getSimpleName();
        String message;

        // 根据异常类型返回友好消息
        if ("SocketTimeoutException".equals(errorType) || "TimeoutException".equals(errorType)) {
            message = "请求超时，请稍后再试";
        } else if (e.getMessage() != null && e.getMessage().contains("JSON")) {
            message = "流程配置错误，请联系管理员";
        } else {
            message = "系统处理时遇到问题，请稍后再试";
        }

        return NodeResult.fail(message, "FLOW_ERROR");
    }

    /**
     * 初始化流程上下文
     * <p>
     * 创建新的上下文并保存到 Redis
     */
    private FlowContext initContext(String userId, String userMessage) {
        FlowContext context = new FlowContext(userId, userMessage);
        contextManager.saveContext(context);
        log.info("流程上下文已初始化: userId={}, sessionId={}", userId, context.getSessionId());
        return context;
    }

    /**
     * 解析流程定义 JSON
     */
    private FlowDefinition parseFlowDefinition(String flowData) {
        if (flowData == null || flowData.isBlank()) {
            return null;
        }

        try {
            return objectMapper.readValue(flowData, FlowDefinition.class);
        } catch (Exception e) {
            log.error("解析流程定义失败: {}", e.getMessage());
            return null;
        }
    }
}

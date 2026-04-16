package com.aip.flow.service.impl;

import com.aip.flow.dto.NodeExecutionPlan;
import com.aip.flow.engine.FlowContext;
import com.aip.flow.engine.NodeResult;
import com.aip.flow.executor.LlmCallExecutor;
import com.aip.flow.executor.NodeExecutor;
import com.aip.flow.service.IContextManager;
import com.aip.flow.service.IDynamicPlannerService;
import com.aip.flow.service.IFlowEngine;
import com.aip.flow.service.INodeRegistryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 流程引擎
 * 负责加载上下文、动态规划并执行节点链路
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowEngine implements IFlowEngine {

    private final IContextManager contextManager;
    private final IDynamicPlannerService dynamicPlannerService;
    private final INodeRegistryService nodeRegistryService;
    private final LlmCallExecutor llmCallExecutor;

    @Override
    public NodeResult execute(String userId, String userMessage) {
        FlowContext context = loadOrCreateContext(userId, userMessage);
        try {
            NodeResult result = executePlan(context, null);
            persistContext(context, result);
            return result;
        } catch (Exception e) {
            log.error("流程执行异常: userId={}, message={}", userId, e.getMessage(), e);
            return NodeResult.fail("系统处理异常，请稍后再试", "FLOW_ERROR");
        }
    }

    @Override
    public NodeResult executeStreaming(String userId, String userMessage, Consumer<String> onChunk) {
        FlowContext context = loadOrCreateContext(userId, userMessage);
        try {
            NodeResult result = executePlan(context, onChunk);
            persistContext(context, result);
            return result;
        } catch (Exception e) {
            log.error("流式流程执行异常: userId={}, message={}", userId, e.getMessage(), e);
            if (onChunk != null) {
                onChunk.accept("系统处理异常，请稍后再试");
            }
            return NodeResult.fail("系统处理异常，请稍后再试", "FLOW_ERROR");
        }
    }

    private FlowContext loadOrCreateContext(String userId, String userMessage) {
        FlowContext context = contextManager.getContext(userId);
        if (context == null) {
            context = new FlowContext(userId);
        }
        context.setUserId(userId);
        context.setCurrentMessage(userMessage);
        return context;
    }

    private NodeResult executePlan(FlowContext context, Consumer<String> onChunk) {
        NodeExecutionPlan plan = dynamicPlannerService.plan(context.getCurrentMessage(), context);
        if (plan == null || plan.isEmpty()) {
            return callLlmFallback(context, onChunk);
        }

        NodeResult lastResult = null;
        for (NodeExecutionPlan.PlannedNode plannedNode : plan.getNodes()) {
            if (plannedNode == null || plannedNode.getNodeType() == null || plannedNode.getNodeType().isBlank()) {
                continue;
            }

            NodeExecutor executor = nodeRegistryService.getExecutor(plannedNode.getNodeType());
            if (executor == null) {
                log.warn("未找到节点执行器: nodeType={}", plannedNode.getNodeType());
                if (plannedNode.isRequired()) {
                    return NodeResult.fail("系统节点不可用: " + plannedNode.getNodeType(), "NODE_NOT_FOUND");
                }
                continue;
            }

            Map<String, Object> nodeConfig = plannedNode.getConfig() != null
                    ? new HashMap<>(plannedNode.getConfig())
                    : new HashMap<>();

            if ("llm_call".equals(plannedNode.getNodeType()) && onChunk != null) {
                lastResult = llmCallExecutor.executeStreaming(context, nodeConfig, onChunk);
            } else {
                lastResult = executor.execute(context, nodeConfig);
                if (onChunk != null && lastResult != null && lastResult.getOutput() != null && !lastResult.getOutput().isBlank()) {
                    onChunk.accept(lastResult.getOutput());
                }
            }

            mergeResultToContext(context, lastResult);

            if (lastResult != null && lastResult.isNeedMoreInput()) {
                if (onChunk != null && lastResult.getPrompt() != null && !lastResult.getPrompt().isBlank()) {
                    onChunk.accept(lastResult.getPrompt());
                }
                return lastResult;
            }
            if (lastResult != null && !lastResult.isSuccess()) {
                return lastResult;
            }
        }

        return lastResult != null ? lastResult : callLlmFallback(context, onChunk);
    }

    private NodeResult callLlmFallback(FlowContext context, Consumer<String> onChunk) {
        Map<String, Object> config = new HashMap<>();
        if (onChunk != null) {
            NodeResult result = llmCallExecutor.executeStreaming(context, config, onChunk);
            mergeResultToContext(context, result);
            return result;
        }

        NodeResult result = llmCallExecutor.execute(context, config);
        mergeResultToContext(context, result);
        return result;
    }

    private void mergeResultToContext(FlowContext context, NodeResult result) {
        if (context == null || result == null) {
            return;
        }

        if (result.getParams() != null && !result.getParams().isEmpty()) {
            context.getParams().putAll(result.getParams());
        }

        if (result.isNeedMoreInput()) {
            context.setStatus("waiting");
        }

        if (result.getPrompt() != null && !result.getPrompt().isBlank()) {
            context.addAssistantMessage(result.getPrompt());
        } else if (result.getOutput() != null && !result.getOutput().isBlank()) {
            context.addAssistantMessage(result.getOutput());
        }

        if (result.getStatus() != null && !result.getStatus().isBlank()) {
            context.setStatus(result.getStatus());
        }
    }

    private void persistContext(FlowContext context, NodeResult result) {
        if (context == null) {
            return;
        }

        context.addUserMessage(context.getCurrentMessage());

        contextManager.saveContext(context);
    }
}

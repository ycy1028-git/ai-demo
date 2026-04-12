package com.aip.flow.executor;

import com.aip.flow.engine.FlowContext;
import com.aip.flow.engine.NodeResult;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 条件判断节点执行器
 * 根据条件选择不同的分支
 */
@Slf4j
@Component
public class ConditionExecutor extends BaseNodeExecutor {

    @PostConstruct
    public void init() {
        initBase("condition", "条件判断", "根据条件判断结果，选择不同的处理分支", "logic",
                Arrays.asList("如果", "当", "判断", "是否", "条件"));
    }

    @Override
    public NodeResult execute(FlowContext context, Map<String, Object> config) {
        try {
            String expression = (String) config.getOrDefault("expression", "");
            Map<String, Object> branches = (Map<String, Object>) config.getOrDefault("branches", new HashMap<>());

            boolean result = evaluateCondition(expression, context);

            String nextNode = result ? "true_branch" : "false_branch";
            
            if (branches.containsKey(nextNode)) {
                Map<String, Object> branch = (Map<String, Object>) branches.get(nextNode);
                String targetNodeId = (String) branch.get("targetNode");
                
                Map<String, Object> params = new HashMap<>();
                params.put("_next_node", targetNodeId);
                params.put("_condition_result", result);
                
                return NodeResult.success("条件判断完成，执行" + (result ? "真" : "假") + "分支", params);
            }

            Map<String, Object> params = new HashMap<>();
            params.put("_condition_result", result);
            
            return NodeResult.success("条件判断完成: " + result, params);

        } catch (Exception e) {
            log.error("条件判断异常: {}", e.getMessage(), e);
            return NodeResult.fail("条件判断失败", "CONDITION_ERROR");
        }
    }

    private boolean evaluateCondition(String expression, FlowContext context) {
        if (expression == null || expression.isBlank()) {
            return true;
        }

        if (expression.contains("包含") || expression.contains("有")) {
            return context.getCurrentMessage().contains(expression.replace("包含", "").replace("有", "").trim());
        }
        
        if (expression.contains("不包含") || expression.contains("没有")) {
            return !context.getCurrentMessage().contains(expression.replace("不包含", "").replace("没有", "").trim());
        }

        return true;
    }
}
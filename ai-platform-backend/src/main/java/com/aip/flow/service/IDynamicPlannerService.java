package com.aip.flow.service;

import com.aip.flow.dto.NodeExecutionPlan;
import com.aip.flow.engine.FlowContext;

/**
 * 动态规划服务接口
 */
public interface IDynamicPlannerService {

    /**
     * 生成节点执行计划
     * @param userMessage 用户消息
     * @param context 流程上下文
     * @return 节点执行计划
     */
    NodeExecutionPlan plan(String userMessage, FlowContext context);

    /**
     * 检查是否需要动态规划
     */
    boolean shouldDynamicPlan(String userMessage);
}

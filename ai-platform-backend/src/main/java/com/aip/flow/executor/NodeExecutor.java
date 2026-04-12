package com.aip.flow.executor;

import com.aip.flow.dto.NodeSchema;
import com.aip.flow.engine.FlowContext;
import com.aip.flow.engine.NodeResult;

import java.util.List;
import java.util.Map;

/**
 * 节点执行器接口
 * 所有节点执行器必须实现此接口，通过 @Component 注解自动注册到 Spring 容器
 * <p>
 * 设计理念：
 * - 每个节点执行器对应一种业务能力（如 LLM 调用、知识检索、参数收集等）
 * - 节点通过 getTriggers() 提供触发词，帮助 LLM 进行路由决策
 * - 节点通过 getInputSchema/getOutputSchema 描述其输入输出，方便流程编排
 * <p>
 * 已实现的节点类型：
 * - llm_call: 调用 AI 大模型生成回复
 * - knowledge_retrieval: 从知识库检索相关内容
 * - router: 根据关键词路由到不同流程
 * - collect: 收集用户输入的参数
 * - condition: 条件分支判断
 * - execute: 执行业务操作（如数据库查询、服务调用等）
 */
public interface NodeExecutor {

    /**
     * 获取节点类型（唯一标识）
     * 用于在流程定义中标识节点类型，如：llm_call, knowledge_retrieval, condition 等
     */
    String getNodeType();

    /**
     * 获取节点名称（显示名称）
     * 用于前端展示，如："AI 对话"、"知识检索"等
     */
    String getNodeName();

    /**
     * 获取节点描述（供 LLM 理解）
     * LLM 根据此描述决定是否使用该节点
     */
    String getDescription();

    /**
     * 获取节点分类
     * 分类用于前端节点面板分组展示：
     * - foundation: 基础节点（start、end、collect、router）
     * - ai: AI 能力节点（llm_call、knowledge_retrieval）
     * - execute: 业务执行节点（order_query、refund_apply 等）
     * - logic: 逻辑控制节点（condition、variable 等）
     * - advanced: 高级能力节点（ocr、tool_call 等）
     */
    String getCategory();

    /**
     * 获取触发词列表
     * 当用户消息包含这些词时，LLM 更倾向于选择该节点
     * 如知识检索节点的触发词：["查询", "搜索", "知识库", "规定", "政策"]
     */
    List<String> getTriggers();

    /**
     * 执行节点
     * <p>
     * 核心业务逻辑在此方法中实现
     *
     * @param context 流程上下文，包含用户消息、历史、已收集参数等
     * @param config 节点配置参数，从流程定义的 flowData 中获取
     * @return 节点执行结果，包含输出内容、收集的参数、是否需要更多输入等
     */
    NodeResult execute(FlowContext context, Map<String, Object> config);

    /**
     * 获取输入参数 Schema
     * 描述该节点需要哪些输入参数，用于流程编排时的参数映射
     */
    NodeSchema getInputSchema();

    /**
     * 获取输出参数 Schema
     * 描述该节点会产出哪些输出参数，供后续节点使用
     */
    NodeSchema getOutputSchema();

    /**
     * 获取配置参数 Schema
     * 描述该节点需要哪些配置参数，管理员在编排流程时填写
     */
    NodeSchema getConfigSchema();
}
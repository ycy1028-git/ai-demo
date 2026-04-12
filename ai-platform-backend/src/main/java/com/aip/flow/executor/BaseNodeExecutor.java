package com.aip.flow.executor;

import com.aip.flow.dto.NodeSchema;
import com.aip.flow.engine.FlowContext;
import com.aip.flow.engine.NodeResult;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * 基础节点执行器
 * 提供通用功能模板，所有具体节点执行器可继承此类
 * <p>
 * 子类只需实现 execute() 方法，其他元数据方法已在 initBase() 中统一处理
 */
@Slf4j
public abstract class BaseNodeExecutor implements NodeExecutor {

    /** 节点类型编码（唯一标识，如 llm_call, knowledge_retrieval） */
    protected String nodeType;

    /** 节点显示名称（用于前端展示） */
    protected String nodeName;

    /** 节点描述（供 LLM 理解该节点的功能） */
    protected String description;

    /** 节点分类：foundation（基础）、ai（AI能力）、execute（业务执行）、logic（逻辑控制）、advanced（高级） */
    protected String category;

    /** 触发词列表（提高 LLM 路由准确率） */
    protected List<String> triggers;

    /**
     * 初始化基础属性
     * @param type 节点类型编码
     * @param name 节点名称
     * @param desc 节点描述
     * @param cat 节点分类
     * @param trigs 触发词列表
     */
    protected void initBase(String type, String name, String desc, String cat, List<String> trigs) {
        this.nodeType = type;
        this.nodeName = name;
        this.description = desc;
        this.category = cat;
        this.triggers = trigs;
    }

    @Override
    public String getNodeType() {
        return nodeType;
    }

    @Override
    public String getNodeName() {
        return nodeName;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public String getCategory() {
        return category;
    }

    @Override
    public List<String> getTriggers() {
        return triggers;
    }

    /**
     * 默认输入 Schema（用户消息）
     */
    @Override
    public NodeSchema getInputSchema() {
        return NodeSchema.builder()
                .type("object")
                .description("用户消息")
                .build();
    }

    /**
     * 默认输出 Schema（处理结果）
     */
    @Override
    public NodeSchema getOutputSchema() {
        return NodeSchema.builder()
                .type("object")
                .description("处理结果")
                .build();
    }

    /**
     * 默认配置 Schema（节点配置）
     */
    @Override
    public NodeSchema getConfigSchema() {
        return NodeSchema.builder()
                .type("object")
                .description("节点配置")
                .build();
    }

    /**
     * 执行节点（子类必须实现）
     * @param context 流程上下文，包含用户消息、历史、参数等
     * @param config 节点配置参数
     * @return 节点执行结果
     */
    @Override
    public NodeResult execute(FlowContext context, Map<String, Object> config) {
        throw new UnsupportedOperationException("子类必须实现 execute 方法");
    }
}
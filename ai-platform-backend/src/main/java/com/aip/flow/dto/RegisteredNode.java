package com.aip.flow.dto;

import com.aip.flow.executor.NodeExecutor;
import lombok.Data;
import lombok.Builder;
import java.util.List;

/**
 * 已注册的节点
 * 封装节点的元数据，供 LLM 理解和路由使用
 */
@Data
@Builder
public class RegisteredNode {

    /** 节点编码（唯一标识，对应 NodeExecutor.getNodeType()） */
    private String code;

    /** 节点名称 */
    private String name;

    /** 能力描述（LLM 可理解） */
    private String description;

    /** 分类（foundation/ai/execute/logic/advanced） */
    private String category;

    /** 触发词列表 */
    private List<String> triggers;

    /** 适用场景 */
    private List<String> scenarios;

    /** 输入参数 Schema */
    private NodeSchema inputSchema;

    /** 输出参数 Schema */
    private NodeSchema outputSchema;

    /** 配置参数 Schema */
    private NodeSchema configSchema;

    /** 关联的执行器 */
    private NodeExecutor executor;
}

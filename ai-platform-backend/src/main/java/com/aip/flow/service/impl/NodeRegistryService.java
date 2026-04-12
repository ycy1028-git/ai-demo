package com.aip.flow.service.impl;

import com.aip.flow.dto.NodeSchema;
import com.aip.flow.dto.RegisteredNode;
import com.aip.flow.executor.NodeExecutor;
import com.aip.flow.service.INodeRegistryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 节点注册服务实现
 * <p>
 * 职责说明：
 * 1. 在应用启动时，从 Spring 容器获取所有 NodeExecutor 实现类
 * 2. 将每个执行器封装为 RegisteredNode，并建立类型到执行器的映射
 * 3. 提供节点查询能力，供流程引擎和前端使用
 * <p>
 * 设计特点：
 * - 利用 @PostConstruct 在 Spring 容器完全初始化后执行注册
 * - 使用 ConcurrentHashMap 保证线程安全
 * - 自动跳过注册失败的节点（可能是接口方法未实现）
 */
@Slf4j
@Service
public class NodeRegistryService implements INodeRegistryService {

    /** 注入所有 NodeExecutor 实现类（Spring 自动收集） */
    @Autowired
    private List<NodeExecutor> nodeExecutors;

    /** 节点类型 -> 执行器的映射，用于快速查找 */
    private final Map<String, NodeExecutor> executorMap = new ConcurrentHashMap<>();

    /** 所有已注册的节点列表（供前端展示） */
    private List<RegisteredNode> registeredNodes;

    /**
     * 初始化方法
     * 在 Spring 容器初始化完成后执行，将所有 NodeExecutor 注册为可用节点
     */
    @PostConstruct
    public void init() {
        // 将所有执行器转换为 RegisteredNode
        registeredNodes = nodeExecutors.stream()
                .map(this::convertToNode)
                .collect(Collectors.toList());

        // 建立类型到执行器的映射
        registeredNodes.forEach(node -> {
            executorMap.put(node.getCode(), node.getExecutor());
        });

        log.info("节点注册完成，共注册 {} 个节点类型", registeredNodes.size());
        // 打印注册信息，便于排查
        registeredNodes.forEach(node -> {
            log.info("  - {}: {}", node.getCode(), node.getDescription());
        });
    }

    /**
     * 获取所有已注册的节点
     * 用于前端节点面板展示
     */
    @Override
    public List<RegisteredNode> getAllNodes() {
        return registeredNodes;
    }

    /**
     * 根据节点类型获取执行器
     * 用于流程引擎执行节点时查找对应的执行器
     *
     * @param nodeType 节点类型编码
     * @return 对应的执行器，不存在返回 null
     */
    @Override
    public NodeExecutor getExecutor(String nodeType) {
        return executorMap.get(nodeType);
    }

    /**
     * 安全获取执行器
     * 返回 Optional，避免空指针
     */
    @Override
    public Optional<NodeExecutor> getExecutorSafe(String nodeType) {
        return Optional.ofNullable(getExecutor(nodeType));
    }

    /**
     * 生成节点描述（Markdown 格式）
     * 用于前端文档展示
     */
    @Override
    public String generateNodeDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append("# 可用节点说明\n\n");

        // 按分类分组展示
        Map<String, List<RegisteredNode>> byCategory = registeredNodes.stream()
                .collect(Collectors.groupingBy(RegisteredNode::getCategory));

        byCategory.forEach((category, nodes) -> {
            sb.append("## ").append(getCategoryName(category)).append("\n");
            nodes.forEach(node -> {
                sb.append(String.format("- **%s**: %s\n",
                        node.getName(), node.getDescription()));
            });
            sb.append("\n");
        });

        return sb.toString();
    }

    /**
     * 生成节点列表（供 LLM 理解）
     * 用于构建路由提示词，帮助 LLM 理解可用节点
     * <p>
     * 输出格式：
     * 【可用节点】
     * - [llm_call] AI对话: 调用AI大模型生成智能回复
     *   触发词: 回答,解释,说明,回复
     * - [knowledge_retrieval] 知识检索: 从知识库检索相关内容
     *   触发词: 查询,搜索,查找
     * ...
     */
    @Override
    public String generateNodesSection() {
        StringBuilder sb = new StringBuilder();
        sb.append("【可用节点】\n");

        registeredNodes.forEach(node -> {
            sb.append(String.format("- [%s] %s: %s\n",
                    node.getCode(),
                    node.getName(),
                    node.getDescription()));

            // 添加触发词（如果有）
            if (node.getTriggers() != null && !node.getTriggers().isEmpty()) {
                sb.append(String.format("  触发词: %s\n", String.join(", ", node.getTriggers())));
            }

            // 添加适用场景（如果有）
            if (node.getScenarios() != null && !node.getScenarios().isEmpty()) {
                sb.append(String.format("  适用场景: %s\n", String.join(", ", node.getScenarios())));
            }
        });

        return sb.toString();
    }

    /**
     * 将 NodeExecutor 转换为 RegisteredNode
     * 封装执行器的元数据，供流程引擎和前端使用
     */
    private RegisteredNode convertToNode(NodeExecutor executor) {
        NodeSchema inputSchema = executor.getInputSchema();
        NodeSchema outputSchema = executor.getOutputSchema();
        NodeSchema configSchema = executor.getConfigSchema();

        return RegisteredNode.builder()
                .code(executor.getNodeType())
                .name(executor.getNodeName())
                .description(executor.getDescription())
                .category(executor.getCategory())
                .triggers(executor.getTriggers())
                .scenarios(new ArrayList<>())
                .inputSchema(inputSchema)
                .outputSchema(outputSchema)
                .configSchema(configSchema)
                .executor(executor)
                .build();
    }

    /**
     * 获取分类显示名称
     */
    private String getCategoryName(String category) {
        return switch (category) {
            case "foundation" -> "基础节点";
            case "ai" -> "AI 能力节点";
            case "execute" -> "业务执行节点";
            case "logic" -> "逻辑控制节点";
            case "advanced" -> "高级能力节点";
            default -> category;
        };
    }
}

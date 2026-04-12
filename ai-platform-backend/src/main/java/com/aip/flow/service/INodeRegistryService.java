package com.aip.flow.service;

import com.aip.flow.dto.RegisteredNode;
import com.aip.flow.executor.NodeExecutor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 节点注册服务接口
 */
public interface INodeRegistryService {

    /**
     * 获取所有已注册的节点
     */
    List<RegisteredNode> getAllNodes();

    /**
     * 根据节点类型获取执行器
     */
    NodeExecutor getExecutor(String nodeType);

    /**
     * 安全获取执行器（不存在时返回空 Optional）
     */
    Optional<NodeExecutor> getExecutorSafe(String nodeType);

    /**
     * 生成节点描述（供 LLM 理解）
     */
    String generateNodeDescription();

    /**
     * 生成统一提示词中的节点列表部分
     */
    String generateNodesSection();
}

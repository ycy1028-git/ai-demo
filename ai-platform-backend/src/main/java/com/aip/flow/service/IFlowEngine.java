package com.aip.flow.service;

import com.aip.flow.engine.NodeResult;
import java.util.function.Consumer;

/**
 * 流程引擎接口
 */
public interface IFlowEngine {

    /**
     * 执行对话
     */
    NodeResult execute(String userId, String userMessage);

    /**
     * 流式执行对话
     */
    NodeResult executeStreaming(String userId, String userMessage, Consumer<String> onChunk);
}

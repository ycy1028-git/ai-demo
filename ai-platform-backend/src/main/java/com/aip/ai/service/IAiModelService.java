package com.aip.ai.service;

import com.aip.ai.entity.AiModelConfig;

import java.util.List;
import java.util.function.Consumer;

/**
 * AI模型服务接口
 * 统一管理不同提供商的AI模型调用
 */
public interface IAiModelService {

    /**
     * 获取所有可用的模型列表
     */
    List<AiModelConfig> getAvailableModels();

    /**
     * 获取模型配置
     */
    AiModelConfig getModelConfig(String modelId);

    /**
     * 获取默认模型
     */
    AiModelConfig getDefaultModel();

    /**
     * 非流式对话
     *
     * @param modelId   模型ID（UUIDv7 无横杠字符串）
     * @param messages 消息列表
     * @return AI回复内容
     */
    String chat(String modelId, List<Message> messages);

    /**
     * 使用默认模型非流式对话
     *
     * @param messages 消息列表
     * @return AI回复内容
     */
    String chatWithDefault(List<Message> messages);

    /**
     * 流式对话
     *
     * @param modelId   模型ID（UUIDv7 无横杠字符串）
     * @param messages 消息列表
     * @param onChunk   每个chunk的回调
     * @param onComplete 完成回调
     * @param onError   错误回调
     */
    void streamChat(String modelId, List<Message> messages,
                    Consumer<String> onChunk, Runnable onComplete, Consumer<String> onError);

    /**
     * 使用默认模型流式对话
     */
    void streamChatWithDefault(List<Message> messages,
                               Consumer<String> onChunk, Runnable onComplete, Consumer<String> onError);

    /**
     * 消息对象
     */
    class Message {
        private String role;       // system, user, assistant
        private String content;    // 消息内容
        private String name;       // 可选，消息发送者名称

        public Message() {}

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }
}

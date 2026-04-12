package com.aip.open.dto;

import lombok.Data;

import java.util.List;

/**
 * 开放API - 聊天请求
 */
@Data
public class OpenChatRequest {

    /**
     * 对话消息列表
     */
    private List<ChatMessage> messages;

    /**
     * 是否流式返回
     */
    private Boolean stream = false;

    /**
     * 系统提示词（可选）
     */
    private String systemPrompt;

    @Data
    public static class ChatMessage {
        /**
         * 角色：user/assistant/system
         */
        private String role;

        /**
         * 消息内容
         */
        private String content;
    }
}

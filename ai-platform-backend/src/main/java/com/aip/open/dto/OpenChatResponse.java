package com.aip.open.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 开放API - 聊天响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpenChatResponse {

    /**
     * 消息ID
     */
    private String id;

    /**
     * 内容
     */
    private String content;

    /**
     * 完成原因（stop/error/max_tokens）
     */
    private String finishReason;

    /**
     * Token使用量
     */
    private Usage usage;

    /**
     * 创建时间戳
     */
    private Long created;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Usage {
        private Integer promptTokens;
        private Integer completionTokens;
        private Integer totalTokens;
    }
}

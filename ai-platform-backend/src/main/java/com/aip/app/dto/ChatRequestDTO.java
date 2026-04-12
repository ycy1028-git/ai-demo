package com.aip.app.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 聊天请求DTO
 */
@Data
public class ChatRequestDTO {

    /** 会话ID（为空则创建新会话） */
    private String sessionId;

    /** 助手代码（如 customer-service） */
    private String assistantCode;

    /** 用户消息 */
    @NotBlank(message = "消息内容不能为空")
    private String message;

    /** 是否流式返回 */
    private Boolean stream = false;
}

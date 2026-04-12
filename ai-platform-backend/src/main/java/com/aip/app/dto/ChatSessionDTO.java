package com.aip.app.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 对话会话DTO
 */
@Data
public class ChatSessionDTO {

    private Long id;

    /** 会话标识 */
    @NotBlank(message = "会话标识不能为空")
    private String sessionId;

    /** 用户ID */
    private Long userId;

    /** 助手ID */
    private Long assistantId;

    /** 助手编码 */
    private String assistantCode;

    /** 会话标题 */
    private String title;

    /** 最新消息摘要 */
    private String lastMessage;

    /** 消息数量 */
    private Integer messageCount;

    /** 状态 */
    private Integer status;
}

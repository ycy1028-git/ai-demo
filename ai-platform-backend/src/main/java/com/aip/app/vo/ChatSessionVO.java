package com.aip.app.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 聊天会话视图对象
 */
@Data
public class ChatSessionVO {

    private String sessionId;

    /** 用户ID */
    private Long userId;

    /** 助手ID */
    private Long assistantId;

    /** 助手编码 */
    private String assistantCode;

    /** 助手名称 */
    private String assistantName;

    /** 会话标题 */
    private String title;

    /** 最后一条消息 */
    private String lastMessage;

    /** 消息数量 */
    private Integer messageCount;

    /** 状态 */
    private Integer status;

    /** 最后活跃时间 */
    private LocalDateTime lastActiveAt;

    /** 创建时间 */
    private LocalDateTime createdAt;
}

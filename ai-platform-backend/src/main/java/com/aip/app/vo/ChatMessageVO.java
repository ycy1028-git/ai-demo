package com.aip.app.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 聊天消息视图对象
 */
@Data
public class ChatMessageVO {

    private Long id;

    /** 会话ID */
    private String sessionId;

    /** 角色：user/assistant/system */
    private String role;

    /** 角色描述 */
    private String roleDesc;

    /** 消息内容 */
    private String content;

    /** 关联知识数量 */
    private Integer relatedCount;

    /** 引用来源 */
    private String citations;

    /** Token数量 */
    private Integer tokens;

    /** 使用的模型 */
    private String model;

    /** 响应时间（毫秒） */
    private Long responseTime;

    /** 是否成功 */
    private Integer success;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /**
     * 获取角色描述
     */
    public static String getRoleDesc(String role) {
        if (role == null) {
            return "未知";
        }
        return switch (role) {
            case "user" -> "用户";
            case "assistant" -> "AI助手";
            case "system" -> "系统";
            default -> role;
        };
    }
}

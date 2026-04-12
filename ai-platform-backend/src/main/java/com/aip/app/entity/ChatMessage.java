package com.aip.app.entity;

import com.aip.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * 对话消息实体
 */
@Getter
@Setter
@Entity
@Table(name = "t_app_chat_message", indexes = {
    @Index(name = "idx_session_id", columnList = "f_session_id"),
    @Index(name = "idx_create_time", columnList = "f_create_time")
})
public class ChatMessage extends BaseEntity {

    /** 所属会话ID */
    @Column(nullable = false, length = 64)
    private String sessionId;

    /** 消息角色：user/assistant/system */
    @Column(nullable = false, length = 20)
    private String role;

    /** 消息内容 */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** 关联的知识库ID列表（JSON数组） */
    @Column(columnDefinition = "JSON")
    private String relatedKbIds;

    /** 检索到的知识条目数量 */
    private Integer relatedCount = 0;

    /** 引用来源（JSON数组，包含知识条目ID和相似度） */
    @Column(columnDefinition = "TEXT")
    private String citations;

    /** Token消耗数量 */
    private Integer tokens = 0;

    /** 模型名称 */
    @Column(length = 50)
    private String model;

    /** 响应耗时（毫秒） */
    private Long responseTime = 0L;

    /** 错误信息 */
    @Column(length = 500)
    private String error;

    /** 是否成功：0失败 1成功 */
    @Column(nullable = false)
    private Integer success = 1;

    /**
     * 本次回答使用的知识库ID
     */
    @Column(length = 32)
    private String knowledgeBaseId;

    /**
     * 本次回答使用的知识库编码
     */
    @Column(length = 50)
    private String knowledgeBaseCode;
}

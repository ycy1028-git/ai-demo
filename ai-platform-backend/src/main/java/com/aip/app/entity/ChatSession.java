package com.aip.app.entity;

import com.aip.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * 对话会话实体
 */
@Getter
@Setter
@Entity
@Table(name = "t_app_chat_session", indexes = {
    @Index(name = "idx_user_id", columnList = "f_user_id"),
    @Index(name = "idx_assistant_id", columnList = "f_assistant_id")
})
public class ChatSession extends BaseEntity {

    /** 会话标识（UUID） */
    @Column(nullable = false, unique = true, length = 64)
    private String sessionId;

    /** 用户ID */
    @Column(nullable = false, length = 32)
    private String userId;

    /** 助手ID（可为空） */
    @Column(length = 32)
    private String assistantId;

    /** 助手编码（可为空） */
    @Column(length = 50)
    private String assistantCode;

    /** 会话标题（可由用户自定义） */
    @Column(length = 100)
    private String title;

    /** 最新消息摘要 */
    @Column(length = 200)
    private String lastMessage;

    /** 消息数量 */
    @Column(nullable = false)
    private Integer messageCount = 0;

    /** 状态：0已归档 1进行中 */
    @Column(nullable = false)
    private Integer status = 1;

    /** 最后活跃时间（毫秒精度） */
    @Column(columnDefinition = "TIMESTAMP(3)")
    private Instant lastActiveAt;

    /**
     * 知识库ID（锁定后不再重新匹配）
     */
    @Column(length = 32)
    private String knowledgeBaseId;

    /**
     * 知识库编码（便于快速查询）
     */
    @Column(length = 50)
    private String knowledgeBaseCode;

    /**
     * 大模型配置ID
     */
    @Column(length = 32)
    private String modelConfigId;
}

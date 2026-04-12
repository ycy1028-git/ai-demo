package com.aip.app.service;

import com.aip.app.dto.ChatRequestDTO;
import com.aip.app.dto.ChatResponseDTO;
import com.aip.app.entity.ChatMessage;
import com.aip.app.entity.ChatSession;
import com.aip.knowledge.entity.KnowledgeBase;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 聊天服务接口
 */
public interface IChatService {

    /**
     * 发送聊天消息（非流式）
     */
    ChatResponseDTO chat(ChatRequestDTO request, String userId);

    /**
     * 创建会话
     */
    ChatSession createSession(String userId, KnowledgeBase knowledgeBase, String firstMessage);

    /**
     * 获取会话
     */
    ChatSession getSession(String sessionId);

    /**
     * 获取用户的所有会话
     */
    List<ChatSession> getUserSessions(String userId);

    /**
     * 获取用户会话（分页）
     */
    Page<ChatSession> getUserSessions(String userId, int page, int size);

    /**
     * 获取会话消息列表
     */
    List<ChatMessage> getSessionMessages(String sessionId);

    /**
     * 获取会话消息（分页）
     */
    Page<ChatMessage> getSessionMessages(String sessionId, int page, int size);

    /**
     * 归档会话
     */
    void archiveSession(String sessionId);

    /**
     * 删除会话
     */
    void deleteSession(String sessionId);

    /**
     * 保存消息
     */
    void saveMessage(ChatMessage message);

    /**
     * 更新会话最后消息
     */
    void updateSessionLastMessage(String sessionId, String lastMessage);
}

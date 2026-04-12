package com.aip.app.service;

import com.aip.app.dto.ChatRequestDTO;
import com.aip.app.dto.ChatResponseDTO;
import com.aip.app.entity.ChatMessage;
import com.aip.app.entity.ChatSession;
import com.aip.app.mapper.ChatMessageMapper;
import com.aip.app.mapper.ChatSessionMapper;
import com.aip.common.util.UuidV7Utils;
import com.aip.knowledge.entity.KnowledgeBase;
import com.aip.knowledge.service.IKnowledgeBaseService;
import com.aip.knowledge.service.IKnowledgeMatchService;
import com.aip.knowledge.service.SearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 对话服务实现
 * 预留服务，用于会话管理和历史记录
 */
@Slf4j
@Service
public class ChatServiceImpl implements IChatService {

    @Autowired
    private ChatSessionMapper sessionMapper;

    @Autowired
    private ChatMessageMapper messageMapper;

    @Autowired
    private IKnowledgeMatchService knowledgeMatchService;

    @Autowired
    private IKnowledgeBaseService knowledgeBaseService;

    @Autowired
    private SearchService searchService;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int SEARCH_TOP_K = 5;

    @Override
    @Transactional
    public ChatResponseDTO chat(ChatRequestDTO request, String userId) {
        long startTime = System.currentTimeMillis();

        // 匹配知识库
        Optional<KnowledgeBase> matchedKb = knowledgeMatchService.matchBest(request.getMessage());

        // 创建会话
        String sessionId = request.getSessionId();
        ChatSession session;
        if (sessionId == null || sessionId.isBlank()) {
            session = createSession(userId, matchedKb.orElse(null), request.getMessage());
        } else {
            session = sessionMapper.findBySessionId(sessionId)
                    .orElseGet(() -> createSession(userId, matchedKb.orElse(null), request.getMessage()));
        }

        // 保存用户消息
        ChatMessage userMessage = new ChatMessage();
        userMessage.setSessionId(session.getSessionId());
        userMessage.setRole("user");
        userMessage.setContent(request.getMessage());
        userMessage.setSuccess(1);
        messageMapper.save(userMessage);

        // 检索知识库
        List<String> relatedKnowledge = new ArrayList<>();
        String esIndex = null;
        if (matchedKb.isPresent()) {
            esIndex = matchedKb.get().getEsIndex();
            try {
                relatedKnowledge = searchService.search(esIndex, request.getMessage(), SEARCH_TOP_K);
            } catch (Exception e) {
                log.warn("知识库检索失败: {}", e.getMessage());
            }
        }

        // 注意：这里只保存消息记录，实际的AI调用由 ChatStreamController 处理
        String reply = "[请使用流式接口获取AI回复]";

        ChatMessage assistantMessage = new ChatMessage();
        assistantMessage.setSessionId(session.getSessionId());
        assistantMessage.setRole("assistant");
        assistantMessage.setContent(reply);
        assistantMessage.setRelatedCount(relatedKnowledge.size());
        assistantMessage.setTokens(0);
        assistantMessage.setResponseTime(System.currentTimeMillis() - startTime);
        assistantMessage.setSuccess(1);
        messageMapper.save(assistantMessage);

        updateSession(session.getSessionId(), reply);

        return ChatResponseDTO.builder()
                .sessionId(session.getSessionId())
                .reply(reply)
                .relatedCount(relatedKnowledge.size())
                .tokens(0)
                .responseTime(assistantMessage.getResponseTime())
                .success(true)
                .build();
    }

    @Override
    @Transactional
    public ChatSession createSession(String userId, KnowledgeBase knowledgeBase, String firstMessage) {
        ChatSession session = new ChatSession();
        session.setSessionId(UuidV7Utils.generateUuidV7String());
        session.setUserId(userId);
        if (knowledgeBase != null) {
            session.setAssistantId(knowledgeBase.getId());
            session.setAssistantCode(knowledgeBase.getCode());
        }
        session.setTitle(truncateTitle(firstMessage));
        session.setLastMessage(truncateMessage(firstMessage));
        session.setMessageCount(0);
        session.setStatus(1);
        session.setLastActiveAt(Instant.now());
        return sessionMapper.save(session);
    }

    @Override
    public ChatSession getSession(String sessionId) {
        return sessionMapper.findBySessionId(sessionId).orElse(null);
    }

    @Override
    public List<ChatSession> getUserSessions(String userId) {
        return sessionMapper.findByUserId(userId);
    }

    @Override
    public Page<ChatSession> getUserSessions(String userId, int page, int size) {
        return sessionMapper.findByUserId(userId, PageRequest.of(page, size));
    }

    @Override
    public List<ChatMessage> getSessionMessages(String sessionId) {
        return messageMapper.findBySessionId(sessionId);
    }

    @Override
    public Page<ChatMessage> getSessionMessages(String sessionId, int page, int size) {
        return messageMapper.findBySessionId(sessionId, PageRequest.of(page, size));
    }

    @Override
    @Transactional
    public void archiveSession(String sessionId) {
        sessionMapper.findBySessionId(sessionId).ifPresent(session -> {
            session.setStatus(0);
            session.markDeleted();
            sessionMapper.save(session);
        });
    }

    @Override
    @Transactional
    public void deleteSession(String sessionId) {
        messageMapper.softDeleteBySessionId(sessionId);
        sessionMapper.findBySessionId(sessionId).ifPresent(session -> {
            session.markDeleted();
            sessionMapper.save(session);
        });
    }

    @Override
    @Transactional
    public void saveMessage(ChatMessage message) {
        messageMapper.save(message);
    }

    @Override
    public void updateSessionLastMessage(String sessionId, String lastMessage) {
        sessionMapper.findBySessionId(sessionId).ifPresent(session -> {
            session.setLastMessage(lastMessage);
            session.setLastActiveAt(Instant.now());
            sessionMapper.save(session);
        });
    }

    private void updateSession(String sessionId, String lastMessage) {
        String summary = truncateMessage(lastMessage);
        sessionMapper.findBySessionId(sessionId).ifPresent(session -> {
            session.setLastMessage(summary);
            session.setLastActiveAt(Instant.now());
            sessionMapper.save(session);
        });
    }

    private String truncateTitle(String message) {
        if (message == null) {
            return "新会话";
        }
        return message.length() > 50 ? message.substring(0, 50) + "..." : message;
    }

    private String truncateMessage(String message) {
        if (message == null) {
            return "";
        }
        return message.length() > 100 ? message.substring(0, 100) + "..." : message;
    }
}

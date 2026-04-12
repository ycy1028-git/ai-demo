package com.aip.app.controller;

import com.aip.ai.entity.AiModelConfig;
import com.aip.ai.service.IAiModelConfigService;
import com.aip.ai.service.IAiModelService;
import com.aip.app.entity.ChatMessage;
import com.aip.app.entity.ChatSession;
import com.aip.app.mapper.ChatMessageMapper;
import com.aip.app.mapper.ChatSessionMapper;
import com.aip.common.config.AsyncConfig;
import com.aip.common.exception.BusinessException;
import com.aip.common.result.Result;
import com.aip.common.utils.UUIDUtil;
import com.aip.knowledge.entity.KnowledgeBase;
import com.aip.knowledge.service.IKnowledgeBaseService;
import com.aip.knowledge.service.IKnowledgeMatchService;
import com.aip.knowledge.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * AI智能助手控制器
 * 支持自动匹配知识库和模型的对话模式
 */
@Slf4j
@Tag(name = "AI智能助手", description = "AI智能助手对话接口")
@RestController
@RequestMapping("/app/chat")
@RequiredArgsConstructor
public class AppChatController {

    @Autowired
    private SearchService searchService;

    @Autowired
    private IAiModelService aiModelService;

    @Autowired
    private IAiModelConfigService aiModelConfigService;

    @Autowired
    private AsyncConfig asyncConfig;

    @Autowired
    private IKnowledgeBaseService knowledgeBaseService;

    @Autowired
    private IKnowledgeMatchService knowledgeMatchService;

    @Autowired
    private ChatSessionMapper sessionMapper;

    @Autowired
    private ChatMessageMapper messageMapper;

    @Value("${ai.chat.default-knowledge-base-code:customer-service}")
    private String defaultKbCode;

    private static final int SEARCH_TOP_K = 5;
    private static final int MAX_HISTORY_MESSAGES = 10;

    /**
     * 获取可用的知识库列表
     */
    @Operation(summary = "获取可用的知识库列表")
    @GetMapping("/knowledge-bases")
    public Result<List<KnowledgeBaseVO>> getKnowledgeBases() {
        List<KnowledgeBase> bases = knowledgeBaseService.list().stream()
                .filter(kb -> kb.getStatus() != null && kb.getStatus() == 1)
                .toList();

        List<KnowledgeBaseVO> result = bases.stream().map(kb -> {
            KnowledgeBaseVO vo = new KnowledgeBaseVO();
            vo.setId(kb.getId().toString());
            vo.setName(kb.getName());
            vo.setCode(kb.getCode());
            vo.setDescription(kb.getDescription());
            return vo;
        }).toList();

        return Result.ok(result);
    }

    /**
     * 创建新会话
     */
    @Operation(summary = "创建新会话")
    @PostMapping("/session")
    public Result<ChatSessionVO> createSession(
            @RequestParam(required = false) String knowledgeBaseId) {
        ChatSession session = new ChatSession();
        session.setSessionId(UUIDUtil.uuid());
        session.setUserId("00000000000000000000000000000001");

        if (knowledgeBaseId != null && !knowledgeBaseId.isBlank()) {
            KnowledgeBase kb = knowledgeBaseService.getById(knowledgeBaseId);
            if (kb == null) {
                throw new BusinessException("知识库不存在");
            }
            session.setKnowledgeBaseId(kb.getId());
            session.setKnowledgeBaseCode(kb.getCode());
        }

        session.setStatus(1);
        session.setLastActiveAt(Instant.now());
        session.setMessageCount(0);
        session = sessionMapper.save(session);

        return Result.ok(toSessionVO(session));
    }

    /**
     * 获取会话历史消息
     */
    @Operation(summary = "获取会话历史消息")
    @GetMapping("/session/{sessionId}/messages")
    public Result<List<ChatMessageVO>> getMessages(
            @PathVariable String sessionId) {
        List<ChatMessage> messages = messageMapper.findBySessionId(sessionId);
        List<ChatMessageVO> result = messages.stream().map(this::toMessageVO).toList();
        return Result.ok(result);
    }

    /**
     * 发送消息（支持自动匹配知识库）
     * 对话开始时，根据消息内容自动匹配知识库和模型
     */
    @Operation(summary = "发送消息")
    @PostMapping("/session/{sessionId}/messages")
    public Result<SendMessageResponse> sendMessage(
            @PathVariable String sessionId,
            @RequestBody SendMessageRequest request) {

        if (request.getContent() == null || request.getContent().isBlank()) {
            throw new BusinessException("消息内容不能为空");
        }

        log.info("收到消息: sessionId={}, content={}", sessionId, request.getContent());

        long startTime = System.currentTimeMillis();

        // 1. 获取或验证会话
        Optional<ChatSession> existingSession = sessionMapper.findBySessionId(sessionId);
        ChatSession session;
        KnowledgeBase kb = null;

        if (existingSession.isPresent()) {
            session = existingSession.get();

            // 如果会话已锁定知识库，验证并使用该知识库
            if (session.getKnowledgeBaseId() != null) {
                kb = knowledgeBaseService.getById(session.getKnowledgeBaseId());
                if (kb == null) {
                    log.warn("会话{}绑定的知识库已不存在，重新匹配", sessionId);
                    session.setKnowledgeBaseId(null);
                    session.setKnowledgeBaseCode(null);
                }
            }

            // 如果会话未锁定知识库且是第一条消息，根据消息内容匹配
            boolean isFirstMessage = session.getMessageCount() == null || session.getMessageCount() == 0;
            if (session.getKnowledgeBaseId() == null && isFirstMessage) {
                kb = matchKnowledgeBase(request.getContent());
                if (kb != null) {
                    session.setKnowledgeBaseId(kb.getId());
                    session.setKnowledgeBaseCode(kb.getCode());
                    log.info("会话{}首次消息匹配知识库: {}", sessionId, kb.getName());
                }
                sessionMapper.save(session);
            } else if (kb == null && session.getKnowledgeBaseId() != null) {
                kb = knowledgeBaseService.getById(session.getKnowledgeBaseId());
            }
        } else {
            // 会话不存在，创建新会话并匹配知识库
            session = new ChatSession();
            session.setSessionId(sessionId);
            session.setUserId("00000000000000000000000000000001");
            session.setStatus(1);
            session.setMessageCount(0);
            session.setLastActiveAt(Instant.now());

            kb = matchKnowledgeBase(request.getContent());
            if (kb != null) {
                session.setKnowledgeBaseId(kb.getId());
                session.setKnowledgeBaseCode(kb.getCode());
                log.info("新会话匹配知识库: {}", kb.getName());
            }

            session = sessionMapper.save(session);
        }

        // 2. 保存用户消息
        ChatMessage userMsg = new ChatMessage();
        userMsg.setSessionId(sessionId);
        userMsg.setRole("user");
        userMsg.setContent(request.getContent());
        userMsg.setKnowledgeBaseId(session.getKnowledgeBaseId());
        userMsg.setKnowledgeBaseCode(session.getKnowledgeBaseCode());
        userMsg.setSuccess(1);
        messageMapper.save(userMsg);

        // 3. 更新会话统计
        session.setMessageCount(session.getMessageCount() + 1);
        session.setLastMessage(truncateMessage(request.getContent()));
        session.setLastActiveAt(Instant.now());
        sessionMapper.save(session);

        // 4. 知识库检索
        String searchContext = "";
        if (kb != null && kb.getEsIndex() != null && !kb.getEsIndex().isBlank()) {
            try {
                List<String> searchResults = searchService.hybridSearch(
                        kb.getEsIndex(), request.getContent(), SEARCH_TOP_K
                );
                searchContext = searchResults.stream()
                        .reduce((a, b) -> a + "\n\n" + b)
                        .orElse("");
                log.info("知识库检索完成: kb={}, 结果长度={}", kb.getName(), searchContext.length());
            } catch (Exception e) {
                log.warn("知识库检索失败: {}", e.getMessage());
            }
        }

        // 5. 获取模型配置
        String modelConfigId = getModelConfigId(kb);
        String modelName = getModelName(modelConfigId);

        // 6. 构建提示词
        String systemPrompt = buildSystemPrompt(kb);
        String fullPrompt = buildFullPrompt(systemPrompt, searchContext, request.getContent());

        // 7. 调用AI
        String reply = callAi(modelConfigId, fullPrompt);
        long responseTime = System.currentTimeMillis() - startTime;

        // 8. 保存助手消息
        ChatMessage assistantMsg = new ChatMessage();
        assistantMsg.setSessionId(sessionId);
        assistantMsg.setRole("assistant");
        assistantMsg.setContent(reply);
        assistantMsg.setModel(modelName);
        assistantMsg.setResponseTime(responseTime);
        assistantMsg.setKnowledgeBaseId(session.getKnowledgeBaseId());
        assistantMsg.setKnowledgeBaseCode(session.getKnowledgeBaseCode());
        assistantMsg.setSuccess(1);
        messageMapper.save(assistantMsg);

        // 9. 更新会话
        session.setMessageCount(session.getMessageCount() + 1);
        session.setLastMessage(truncateMessage(reply));
        session.setLastActiveAt(Instant.now());
        sessionMapper.save(session);

        // 10. 返回响应
        SendMessageResponse response = new SendMessageResponse();
        response.setMessageId(assistantMsg.getId().toString());
        response.setContent(reply);
        response.setModel(modelName);
        response.setResponseTime(responseTime);
        response.setKnowledgeBase(kb != null ? toKnowledgeBaseVO(kb) : null);

        return Result.ok(response);
    }

    /**
     * 流式对话（指定知识库）
     */
    @Operation(summary = "流式对话")
    @GetMapping("/stream")
    public SseEmitter chatStream(
            @Parameter(description = "用户问题", required = true)
            @RequestParam String message,
            @Parameter(description = "会话ID")
            @RequestParam(required = false) String sessionId,
            @Parameter(description = "知识库ID（可选）")
            @RequestParam(required = false) String knowledgeBaseId) {

        if (message == null || message.isBlank()) {
            throw new BusinessException("问题不能为空");
        }

        log.info("收到流式对话请求: sessionId={}, kbId={}, message={}", sessionId, knowledgeBaseId, message);

        // 1. 获取或创建会话
        ChatSession session = getOrCreateSession(sessionId, knowledgeBaseId);

        // 2. 获取知识库
        KnowledgeBase kb = null;
        if (session.getKnowledgeBaseId() != null) {
            kb = knowledgeBaseService.getById(session.getKnowledgeBaseId());
        }

        // 3. 获取模型配置
        String modelConfigId = getModelConfigId(kb);

        // 4. 构建系统提示词
        String systemPrompt = buildSystemPrompt(kb);

        // 5. 知识库检索
        String searchContext = "";
        if (kb != null && kb.getEsIndex() != null) {
            try {
                List<String> searchResults = searchService.hybridSearch(
                        kb.getEsIndex(), message, SEARCH_TOP_K
                );
                searchContext = searchResults.stream()
                        .reduce((a, b) -> a + "\n\n" + b)
                        .orElse("");
                log.info("知识库检索完成: kb={}, 结果长度={}", kb.getName(), searchContext.length());
            } catch (Exception e) {
                log.warn("知识库检索失败: {}", e.getMessage());
            }
        }

        // 6. 构建完整提示词
        String fullPrompt = buildFullPrompt(systemPrompt, searchContext, message);

        // 7. 获取历史消息
        List<IAiModelService.Message> messages = buildMessagesWithHistory(session, fullPrompt, message);

        // 8. 创建SSE emitter
        SseEmitter emitter = new SseEmitter(300L * 1000);
        emitter.onCompletion(() -> log.debug("SSE连接完成"));
        emitter.onTimeout(() -> log.debug("SSE连接超时"));
        emitter.onError(e -> log.debug("SSE连接错误: {}", e.getMessage()));

        // 9. 异步执行流式请求
        ThreadPoolTaskExecutor executor = asyncConfig.chatTaskExecutor();
        executor.execute(() -> {
            processStreamChat(session, modelConfigId, messages, emitter);
        });

        return emitter;
    }

    /**
     * 获取或创建会话
     */
    private ChatSession getOrCreateSession(String sessionId, String knowledgeBaseId) {
        if (sessionId != null && !sessionId.isBlank()) {
            Optional<ChatSession> existing = sessionMapper.findBySessionId(sessionId);
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        ChatSession session = new ChatSession();
        session.setSessionId(UUIDUtil.uuid());
        session.setUserId("00000000000000000000000000000001");

        if (knowledgeBaseId != null && !knowledgeBaseId.isBlank()) {
            KnowledgeBase kb = knowledgeBaseService.getById(knowledgeBaseId);
            if (kb != null) {
                session.setKnowledgeBaseId(kb.getId());
                session.setKnowledgeBaseCode(kb.getCode());
            }
        }

        session.setStatus(1);
        session.setLastActiveAt(Instant.now());
        session.setMessageCount(0);
        return sessionMapper.save(session);
    }

    /**
     * 匹配知识库
     * 1. 优先使用关键词匹配
     * 2. 向量相似度匹配作为补充
     * 3. 最终使用默认客服知识库兜底
     */
    private KnowledgeBase matchKnowledgeBase(String userQuestion) {
        if (userQuestion == null || userQuestion.isBlank()) {
            return getDefaultKnowledgeBase();
        }

        try {
            // 使用知识库匹配服务（支持关键词+向量混合匹配）
            Optional<KnowledgeBase> matchedKb = knowledgeMatchService.matchBest(userQuestion);
            if (matchedKb.isPresent()) {
                log.info("知识库匹配成功: {} (关键词+向量匹配)", matchedKb.get().getName());
                return matchedKb.get();
            }
        } catch (Exception e) {
            log.warn("知识库匹配失败: {}", e.getMessage());
        }

        // 如果没有匹配到，返回默认客服知识库
        return getDefaultKnowledgeBase();
    }

    /**
     * 获取默认客服知识库
     */
    private KnowledgeBase getDefaultKnowledgeBase() {
        try {
            KnowledgeBase defaultKb = knowledgeBaseService.getByCode(defaultKbCode);
            if (defaultKb != null && defaultKb.getStatus() != null && defaultKb.getStatus() == 1) {
                log.debug("使用默认客服知识库: {}", defaultKb.getName());
                return defaultKb;
            }
        } catch (Exception e) {
            log.warn("获取默认客服知识库失败: {}", e.getMessage());
        }

        // 如果默认知识库不可用，尝试获取第一个可用的知识库
        try {
            List<KnowledgeBase> allKb = knowledgeBaseService.list().stream()
                    .filter(kb -> kb.getStatus() != null && kb.getStatus() == 1)
                    .toList();
            if (!allKb.isEmpty()) {
                log.debug("使用第一个可用知识库作为默认: {}", allKb.get(0).getName());
                return allKb.get(0);
            }
        } catch (Exception e) {
            log.warn("获取可用知识库失败: {}", e.getMessage());
        }

        return null;
    }

    /**
     * 获取模型配置ID
     */
    private String getModelConfigId(KnowledgeBase kb) {
        // 优先使用知识库关联的模型
        if (kb != null && kb.getModelConfigId() != null) {
            return kb.getModelConfigId();
        }
        // 使用默认模型
        try {
            AiModelConfig defaultModel = aiModelConfigService.getDefaultModel();
            return defaultModel != null ? defaultModel.getId() : null;
        } catch (Exception e) {
            log.warn("获取默认模型失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 调用AI（非流式）
     */
    private String callAi(String modelConfigId, String prompt) {
        try {
            List<IAiModelService.Message> messages = List.of(
                    new IAiModelService.Message("user", prompt)
            );

            if (modelConfigId != null) {
                return aiModelService.chat(modelConfigId, messages);
            } else {
                return aiModelService.chatWithDefault(messages);
            }
        } catch (Exception e) {
            log.error("AI调用失败: {}", e.getMessage());
            return "抱歉，AI服务暂时不可用，请稍后再试。";
        }
    }

    /**
     * 构建消息列表
     */
    private List<IAiModelService.Message> buildMessagesWithHistory(
            ChatSession session, String systemPrompt, String currentMessage) {
        List<IAiModelService.Message> messages = new ArrayList<>();

        messages.add(new IAiModelService.Message("system", systemPrompt));

        if (session.getSessionId() != null) {
            List<ChatMessage> history = messageMapper.findBySessionId(session.getSessionId());
            if (history != null && !history.isEmpty()) {
                int skipCount = Math.max(0, history.size() - MAX_HISTORY_MESSAGES);
                for (ChatMessage msg : history.subList(skipCount, history.size())) {
                    messages.add(new IAiModelService.Message(msg.getRole(), msg.getContent()));
                }
            }
        }

        messages.add(new IAiModelService.Message("user", currentMessage));
        return messages;
    }

    /**
     * 处理流式聊天
     */
    private void processStreamChat(ChatSession session, String modelConfigId,
                                  List<IAiModelService.Message> messages, SseEmitter emitter) {
        StringBuilder fullResponse = new StringBuilder();
        Object lock = new Object();
        AtomicBoolean completed = new AtomicBoolean(false);
        long startTime = System.currentTimeMillis();

        String modelName = getModelName(modelConfigId);

        try {
            saveUserMessage(session, messages);

            Consumer<String> onChunk = chunk -> {
                synchronized (lock) {
                    fullResponse.append(chunk);
                }
                sendSseChunk(emitter, chunk);
            };

            Runnable onComplete = () -> {
                synchronized (lock) {
                    completed.set(true);
                    lock.notifyAll();
                }
            };

            Consumer<String> onError = error -> {
                synchronized (lock) {
                    completed.set(true);
                    lock.notifyAll();
                }
                sendSseError(emitter, error);
            };

            if (modelConfigId != null) {
                aiModelService.streamChat(modelConfigId, messages, onChunk, onComplete, onError);
            } else {
                aiModelService.streamChatWithDefault(messages, onChunk, onComplete, onError);
            }

            synchronized (lock) {
                while (!completed.get()) {
                    try {
                        lock.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

            log.info("对话完成, 响应长度: {}", fullResponse.length());

            long responseTime = System.currentTimeMillis() - startTime;
            saveAssistantMessage(session, fullResponse.toString(), modelName, responseTime);

        } catch (Exception e) {
            log.error("AI流式调用异常", e);
            sendSseError(emitter, "AI服务暂时不可用: " + e.getMessage());
        } finally {
            try {
                emitter.complete();
            } catch (Exception e) {
                log.warn("关闭SSE连接失败: {}", e.getMessage());
            }
        }
    }

    private String getModelName(String modelConfigId) {
        try {
            if (modelConfigId != null) {
                AiModelConfig config = aiModelConfigService.getModelById(modelConfigId);
                return config != null ? config.getModelName() : null;
            }
            AiModelConfig config = aiModelConfigService.getDefaultModel();
            return config != null ? config.getModelName() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void saveUserMessage(ChatSession session, List<IAiModelService.Message> messages) {
        try {
            for (int i = messages.size() - 1; i >= 0; i--) {
                if ("user".equals(messages.get(i).getRole())) {
                    ChatMessage userMsg = new ChatMessage();
                    userMsg.setSessionId(session.getSessionId());
                    userMsg.setRole("user");
                    userMsg.setContent(messages.get(i).getContent());
                    userMsg.setKnowledgeBaseId(session.getKnowledgeBaseId());
                    userMsg.setKnowledgeBaseCode(session.getKnowledgeBaseCode());
                    userMsg.setSuccess(1);
                    messageMapper.save(userMsg);
                    updateSessionStats(session, 1);
                    break;
                }
            }
        } catch (Exception e) {
            log.warn("保存用户消息失败: {}", e.getMessage());
        }
    }

    private void saveAssistantMessage(ChatSession session, String content,
                                      String modelName, long responseTime) {
        try {
            ChatMessage assistantMsg = new ChatMessage();
            assistantMsg.setSessionId(session.getSessionId());
            assistantMsg.setRole("assistant");
            assistantMsg.setContent(content);
            assistantMsg.setModel(modelName);
            assistantMsg.setResponseTime(responseTime);
            assistantMsg.setKnowledgeBaseId(session.getKnowledgeBaseId());
            assistantMsg.setKnowledgeBaseCode(session.getKnowledgeBaseCode());
            assistantMsg.setSuccess(1);
            messageMapper.save(assistantMsg);

            session.setLastMessage(truncateMessage(content));
            session.setLastActiveAt(Instant.now());
            sessionMapper.save(session);
        } catch (Exception e) {
            log.warn("保存助手消息失败: {}", e.getMessage());
        }
    }

    private void updateSessionStats(ChatSession session, int delta) {
        try {
            session.setMessageCount(session.getMessageCount() + delta);
            session.setLastActiveAt(Instant.now());
            sessionMapper.save(session);
        } catch (Exception e) {
            log.warn("更新会话统计失败: {}", e.getMessage());
        }
    }

    private String truncateMessage(String message) {
        if (message == null) return "";
        return message.length() > 200 ? message.substring(0, 200) + "..." : message;
    }

    private void sendSseChunk(SseEmitter emitter, String chunk) {
        try {
            String jsonData = "{\"content\": \"" + escapeJson(chunk) + "\"}";
            emitter.send(SseEmitter.event().name("message").data(jsonData));
        } catch (Exception e) {
            log.warn("发送SSE事件失败: {}", e.getMessage());
        }
    }

    private void sendSseError(SseEmitter emitter, String error) {
        try {
            emitter.send(SseEmitter.event().name("error").data("{\"error\": \"" + escapeJson(error) + "\"}"));
        } catch (Exception e) {
            log.warn("发送错误事件失败: {}", e.getMessage());
        }
    }

    private String buildSystemPrompt(KnowledgeBase kb) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一个专业的AI智能助手。\n");

        if (kb != null) {
            prompt.append("\n【当前业务场景】\n");
            prompt.append("你正在回答与「").append(kb.getName()).append("」相关的问题。\n");

            if (kb.getSceneDescription() != null && !kb.getSceneDescription().isBlank()) {
                prompt.append("场景说明：").append(kb.getSceneDescription()).append("\n");
            }
        } else {
            prompt.append("\n【通用AI助手】\n");
            prompt.append("请基于你的知识库为用户提供专业、准确的回答。\n");
        }

        return prompt.toString();
    }

    private String buildFullPrompt(String systemPrompt, String searchContext, String userMessage) {
        StringBuilder fullPrompt = new StringBuilder();
        fullPrompt.append(systemPrompt).append("\n\n");

        if (searchContext != null && !searchContext.isBlank()) {
            fullPrompt.append("【参考知识库内容】\n")
                    .append(searchContext)
                    .append("\n\n");
            fullPrompt.append("请根据上述参考内容准确回答用户的问题。如果参考内容中没有相关信息，请基于你的知识库进行回答，但请明确说明这一点。\n\n");
        }

        fullPrompt.append("【用户问题】\n").append(userMessage);
        return fullPrompt.toString();
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private KnowledgeBaseVO toKnowledgeBaseVO(KnowledgeBase kb) {
        KnowledgeBaseVO vo = new KnowledgeBaseVO();
        vo.setId(kb.getId().toString());
        vo.setName(kb.getName());
        vo.setCode(kb.getCode());
        vo.setDescription(kb.getDescription());
        return vo;
    }

    // VO转换方法
    private ChatSessionVO toSessionVO(ChatSession session) {
        ChatSessionVO vo = new ChatSessionVO();
        vo.setSessionId(session.getSessionId());
        vo.setKnowledgeBaseId(session.getKnowledgeBaseId() != null ? session.getKnowledgeBaseId().toString() : null);
        vo.setKnowledgeBaseCode(session.getKnowledgeBaseCode());
        vo.setMessageCount(session.getMessageCount());
        vo.setLastMessage(session.getLastMessage());
        vo.setLastActiveAt(session.getLastActiveAt() != null ? session.getLastActiveAt().toString() : null);
        return vo;
    }

    private ChatMessageVO toMessageVO(ChatMessage msg) {
        ChatMessageVO vo = new ChatMessageVO();
        vo.setId(msg.getId() != null ? msg.getId().toString() : null);
        vo.setRole(msg.getRole());
        vo.setContent(msg.getContent());
        vo.setModel(msg.getModel());
        vo.setResponseTime(msg.getResponseTime());
        vo.setCreateTime(msg.getCreateTime() != null ? msg.getCreateTime().toString() : null);
        return vo;
    }

    // VO类
    @lombok.Data
    public static class KnowledgeBaseVO {
        private String id;
        private String name;
        private String code;
        private String description;
    }

    @lombok.Data
    public static class ChatSessionVO {
        private String sessionId;
        private String knowledgeBaseId;
        private String knowledgeBaseCode;
        private Integer messageCount;
        private String lastMessage;
        private String lastActiveAt;
    }

    @lombok.Data
    public static class ChatMessageVO {
        private String id;
        private String role;
        private String content;
        private String model;
        private Long responseTime;
        private String createTime;
    }

    /**
     * 发送消息请求
     */
    @lombok.Data
    public static class SendMessageRequest {
        private String content;
    }

    /**
     * 发送消息响应
     */
    @lombok.Data
    public static class SendMessageResponse {
        private String messageId;
        private String content;
        private String model;
        private Long responseTime;
        private KnowledgeBaseVO knowledgeBase;
    }
}

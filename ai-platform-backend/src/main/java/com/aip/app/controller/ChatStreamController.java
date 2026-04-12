package com.aip.app.controller;

import com.aip.ai.entity.AiModelConfig;
import com.aip.ai.service.IAiModelConfigService;
import com.aip.ai.service.IAiModelService;
import com.aip.app.entity.ChatMessage;
import com.aip.app.entity.ChatSession;
import com.aip.app.mapper.ChatMessageMapper;
import com.aip.app.mapper.ChatSessionMapper;
import com.aip.app.service.IChatService;
import com.aip.common.config.AsyncConfig;
import com.aip.common.exception.BusinessException;
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
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 聊天流式控制器
 * 统一的AI智能助手接口
 */
@Slf4j
@Tag(name = "AI聊天", description = "AI智能助手聊天接口")
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatStreamController {

    @Autowired
    private IKnowledgeMatchService knowledgeMatchService;

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
    private ChatSessionMapper sessionMapper;

    @Autowired
    private ChatMessageMapper messageMapper;

    private static final int SEARCH_TOP_K = 5;
    private static final int MAX_HISTORY_MESSAGES = 10;  // 最多保留10条历史消息

    /**
     * 检查AI模型配置状态
     */
    @Operation(summary = "检查AI服务状态")
    @GetMapping("/status")
    public String checkStatus() {
        try {
            // 尝试获取默认模型
            aiModelService.getDefaultModel();
            return "AI服务正常";
        } catch (Exception e) {
            return "AI服务未配置: " + e.getMessage();
        }
    }

    /**
     * 获取知识库列表（供前端显示）
     */
    @Operation(summary = "获取知识库列表")
    @GetMapping("/knowledge-bases")
    public List<KnowledgeBase> getKnowledgeBases() {
        return knowledgeMatchService.matchTop("", 10);
    }

    /**
     * 流式对话接口
     * 根据用户问题自动匹配知识库进行RAG增强
     * 支持会话锁定：首次匹配后锁定知识库，后续消息复用
     */
    @Operation(summary = "流式对话")
    @GetMapping("/stream")
    public SseEmitter streamChat(
            @Parameter(description = "用户问题", required = true)
            @RequestParam String message,
            @Parameter(description = "会话ID（可选）")
            @RequestParam(required = false) String sessionId
    ) {
        if (message == null || message.isBlank()) {
            throw new BusinessException("问题不能为空");
        }

        log.info("收到聊天请求: sessionId={}, message={}", sessionId, message);

        // 1. 获取或创建会话
        ChatSession session = getOrCreateSession(sessionId);

        // 2. 匹配知识库（会话锁定后不再重新匹配）
        KnowledgeBase matchedKb = getSessionKnowledgeBase(session, message);

        // 3. 获取模型配置
        String modelConfigId = getSessionModelConfigId(session, matchedKb);

        // 4. 构建系统提示词
        String systemPrompt = buildSystemPrompt(matchedKb);

        // 5. 知识库检索
        String searchContext = "";
        if (matchedKb != null) {
            try {
                searchContext = searchService.search(
                        matchedKb.getEsIndex(),
                        message,
                        SEARCH_TOP_K
                ).stream()
                        .reduce((a, b) -> a + "\n\n" + b)
                        .orElse("");
                log.info("知识库检索完成: kb={}, 结果长度={}",
                        matchedKb.getName(), searchContext.length());
            } catch (Exception e) {
                log.warn("知识库检索失败: {}", e.getMessage());
            }
        }

        // 6. 构建完整提示词（包含知识库上下文）
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
    private ChatSession getOrCreateSession(String sessionId) {
        if (sessionId != null && !sessionId.isBlank()) {
            Optional<ChatSession> existing = sessionMapper.findBySessionId(sessionId);
            if (existing.isPresent()) {
                return existing.get();
            }
        }
        // 创建新会话
        ChatSession session = new ChatSession();
        session.setSessionId(UUIDUtil.uuid());
        session.setUserId("00000000000000000000000000000001"); // 默认用户ID
        session.setStatus(1);
        session.setLastActiveAt(Instant.now());
        session.setMessageCount(0);
        return sessionMapper.save(session);
    }

    /**
     * 获取会话关联的知识库（支持会话锁定）
     * - 如果会话已锁定知识库，直接返回
     * - 如果会话未锁定，匹配后锁定
     */
    private KnowledgeBase getSessionKnowledgeBase(ChatSession session, String message) {
        // 如果会话已锁定知识库，直接返回
        if (session.getKnowledgeBaseId() != null) {
            KnowledgeBase kb = knowledgeBaseService.getById(session.getKnowledgeBaseId());
            if (kb != null && kb.getStatus() == 1) {
                return kb;
            }
            // 如果知识库不可用，清除锁定
            return null;
        }

        // 匹配知识库
        Optional<KnowledgeBase> matchedKb = knowledgeMatchService.matchBest(message);
        if (matchedKb.isPresent()) {
            // 锁定知识库
            KnowledgeBase kb = matchedKb.get();
            session.setKnowledgeBaseId(kb.getId());
            session.setKnowledgeBaseCode(kb.getCode());
            sessionMapper.save(session);
            log.info("会话锁定知识库: sessionId={}, kbId={}, kbName={}",
                    session.getSessionId(), kb.getId(), kb.getName());
            return kb;
        }
        return null;
    }

    /**
     * 获取会话使用的模型配置
     * - 优先使用会话锁定的模型
     * - 其次使用知识库关联的模型
     * - 最后使用系统默认模型
     */
    private String getSessionModelConfigId(ChatSession session, KnowledgeBase matchedKb) {
        // 如果会话已锁定模型，直接返回
        if (session.getModelConfigId() != null) {
            return session.getModelConfigId();
        }

        // 如果知识库关联了特定模型，使用该模型
        if (matchedKb != null && matchedKb.getModelConfigId() != null) {
            // 锁定模型
            session.setModelConfigId(matchedKb.getModelConfigId());
            sessionMapper.save(session);
            log.info("会话锁定模型: sessionId={}, modelConfigId={}",
                    session.getSessionId(), matchedKb.getModelConfigId());
            return matchedKb.getModelConfigId();
        }

        // 使用系统默认模型（不锁定，因为默认模型可能变化）
        return null;
    }

    /**
     * 构建消息列表（包含历史上下文）
     */
    private List<IAiModelService.Message> buildMessagesWithHistory(
            ChatSession session, String systemPrompt, String currentMessage) {
        List<IAiModelService.Message> messages = new ArrayList<>();

        // 1. 添加系统提示词
        messages.add(new IAiModelService.Message("system", systemPrompt));

        // 2. 添加历史消息（限制数量）
        if (session.getSessionId() != null) {
            List<ChatMessage> history = messageMapper.findBySessionId(session.getSessionId());
            // 跳过最早的 N 条消息，保留最近的对话历史
            int skipCount = Math.max(0, history.size() - MAX_HISTORY_MESSAGES);
            for (ChatMessage msg : history.subList(skipCount, history.size())) {
                messages.add(new IAiModelService.Message(msg.getRole(), msg.getContent()));
            }
        }

        // 3. 添加当前用户消息
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

        // 获取模型名称用于记录
        String modelName = null;
        try {
            if (modelConfigId != null) {
                AiModelConfig config = aiModelConfigService.getModelById(modelConfigId);
                if (config != null) {
                    modelName = config.getModelName();
                }
            } else {
                AiModelConfig config = aiModelConfigService.getDefaultModel();
                if (config != null) {
                    modelName = config.getModelName();
                }
            }
        } catch (Exception e) {
            log.warn("获取模型名称失败: {}", e.getMessage());
        }

        try {
            // 保存用户消息
            saveUserMessage(session, messages);

            // 流式调用
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
                log.debug("AI流式响应完成");
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
                // 提取消息内容（去掉system消息）
                List<IAiModelService.Message> chatMessages = messages.stream()
                        .filter(m -> !"system".equals(m.getRole()))
                        .toList();
                aiModelService.streamChatWithDefault(chatMessages, onChunk, onComplete, onError);
            }

            // 等待完成
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

            // 保存助手消息
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

    /**
     * 保存用户消息
     */
    private void saveUserMessage(ChatSession session, List<IAiModelService.Message> messages) {
        try {
            // 找到用户消息
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

                    // 更新会话统计
                    updateSessionStats(session, 1);
                    break;
                }
            }
        } catch (Exception e) {
            log.warn("保存用户消息失败: {}", e.getMessage());
        }
    }

    /**
     * 保存助手消息
     */
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

            // 更新会话
            updateSessionLastMessage(session, content);
        } catch (Exception e) {
            log.warn("保存助手消息失败: {}", e.getMessage());
        }
    }

    /**
     * 更新会话统计
     */
    private void updateSessionStats(ChatSession session, int delta) {
        try {
            session.setMessageCount(session.getMessageCount() + delta);
            session.setLastActiveAt(Instant.now());
            sessionMapper.save(session);
        } catch (Exception e) {
            log.warn("更新会话统计失败: {}", e.getMessage());
        }
    }

    /**
     * 更新会话最后消息
     */
    private void updateSessionLastMessage(ChatSession session, String lastMessage) {
        try {
            session.setLastMessage(truncateMessage(lastMessage));
            session.setLastActiveAt(Instant.now());
            sessionMapper.save(session);
        } catch (Exception e) {
            log.warn("更新会话最后消息失败: {}", e.getMessage());
        }
    }

    private String truncateMessage(String message) {
        if (message == null) return "";
        return message.length() > 200 ? message.substring(0, 200) + "..." : message;
    }

    /**
     * 发送SSE数据块
     */
    private void sendSseChunk(SseEmitter emitter, String chunk) {
        try {
            String jsonData = "{\"content\": \"" + escapeJson(chunk) + "\"}";
            emitter.send(SseEmitter.event()
                    .name("message")
                    .data(jsonData));
        } catch (Exception e) {
            log.warn("发送SSE事件失败: {}", e.getMessage());
        }
    }

    /**
     * 发送SSE错误
     */
    private void sendSseError(SseEmitter emitter, String error) {
        try {
            emitter.send(SseEmitter.event()
                    .name("error")
                    .data("{\"error\": \"" + escapeJson(error) + "\"}"));
        } catch (Exception e) {
            log.warn("发送错误事件失败: {}", e.getMessage());
        }
    }

    /**
     * 构建系统提示词
     */
    private String buildSystemPrompt(KnowledgeBase knowledgeBase) {
        StringBuilder prompt = new StringBuilder();

        // 基础角色
        prompt.append("你是一个专业的AI智能助手。\n");

        // 知识库场景
        if (knowledgeBase != null) {
            prompt.append("\n【当前业务场景】\n");
            prompt.append("你正在回答与「").append(knowledgeBase.getName()).append("」相关的问题。\n");

            if (knowledgeBase.getSceneDescription() != null
                    && !knowledgeBase.getSceneDescription().isBlank()) {
                prompt.append("场景说明：").append(knowledgeBase.getSceneDescription()).append("\n");
            }
        } else {
            prompt.append("\n【通用AI助手】\n");
            prompt.append("请基于你的知识库为用户提供专业、准确的回答。\n");
        }

        return prompt.toString();
    }

    /**
     * 构建完整提示词（包含知识库上下文）
     */
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

    /**
     * JSON字符串转义
     */
    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}

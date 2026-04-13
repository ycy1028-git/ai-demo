package com.aip.flow.controller;

import com.aip.common.exception.BusinessException;
import com.aip.common.result.Result;
import com.aip.common.security.LoginUser;
import com.aip.flow.engine.FlowContext;
import com.aip.flow.engine.NodeResult;
import com.aip.flow.executor.LlmCallExecutor;
import com.aip.flow.service.IContextManager;
import com.aip.flow.service.IFlowEngine;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.concurrent.DelegatingSecurityContextExecutorService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AI 对话控制器
 * <p>
 * 提供统一的对话接口，支持：
 * - SSE 流式响应（实时打字效果）
 * - 多轮对话上下文管理
 * - 意图路由和节点执行
 */
@Slf4j
@RestController("flowChatController")
@RequestMapping("/flow/chat")
@RequiredArgsConstructor
public class ChatController {

    private final IFlowEngine flowEngine;
    private final IContextManager contextManager;
    private final LlmCallExecutor llmCallExecutor;

    private final ExecutorService asyncExecutor = new DelegatingSecurityContextExecutorService(
            Executors.newCachedThreadPool()
    );

    /** SSE 连接超时时间：5分钟 */
    private static final long SSE_TIMEOUT = 300L * 1000;

    /**
     * 流式对话接口
     * <p>
     * 使用 SSE（Server-Sent Events）实现流式响应，AI 回复实时显示
     */
    @GetMapping("/stream")
    public SseEmitter chatStream(@RequestParam String message,
                                  @RequestParam(required = false) String sessionId,
                                  HttpServletRequest request) {

        if (message == null || message.isBlank()) {
            throw new BusinessException("问题不能为空");
        }

        String contextKey = resolveContextKey(sessionId, request);
        log.info("收到对话请求: contextKey={}, sessionId={}, message={}", contextKey, sessionId, message);

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        emitter.onCompletion(() -> log.debug("SSE连接完成: contextKey={}", contextKey));
        emitter.onTimeout(() -> log.debug("SSE连接超时: contextKey={}", contextKey));
        emitter.onError(e -> log.debug("SSE连接错误: contextKey={}, error={}", contextKey, e.getMessage()));

        asyncExecutor.execute(() -> {
            try {
                // 使用流式执行
                NodeResult result = flowEngine.executeStreaming(contextKey, message, (chunk) -> {
                    try {
                        String json = String.format("{\"type\":\"content\",\"content\":\"%s\"}", escapeJson(chunk));
                        // 直接发送 data 格式，不使用命名事件（避免前端 fetch 解析兼容性问题）
                        emitter.send(SseEmitter.event().data(json));
                    } catch (IOException e) {
                        log.debug("流式发送失败: {}", e.getMessage());
                    }
                });

                // 发送完成状态
                String statusJson = String.format("{\"type\":\"status\",\"content\":\"done\"}");
                emitter.send(SseEmitter.event().data(statusJson));

                emitter.complete();

            } catch (Exception e) {
                log.error("对话处理异常: contextKey={}", contextKey, e);
                handleStreamError(emitter, e);
            } finally {
                // 流式处理完成后清除 SecurityContext
                SecurityContextHolder.clearContext();
            }
        });

        return emitter;
    }

    /**
     * 获取当前对话上下文
     */
    @GetMapping("/context")
    public Result<FlowContext> getContext(@RequestParam(required = false) String sessionId,
                                          HttpServletRequest request) {
        String contextKey = resolveContextKey(sessionId, request);
        FlowContext context = contextManager.getContext(contextKey);
        return Result.ok(context);
    }

    /**
     * 清除对话上下文
     */
    @DeleteMapping("/context")
    public Result<Void> clearContext(@RequestParam(required = false) String sessionId,
                                     HttpServletRequest request) {
        String contextKey = resolveContextKey(sessionId, request);
        contextManager.clearContext(contextKey);
        return Result.ok();
    }

    /**
     * 发送 SSE 数据
     */
    private void sendData(SseEmitter emitter, String eventName, String data) {
        try {
            String json = String.format("{\"type\":\"%s\",\"content\":\"%s\"}", eventName, escapeJson(data));
            emitter.send(SseEmitter.event().name(eventName).data(json));
        } catch (IOException e) {
            log.debug("SSE发送失败: {}", e.getMessage());
        }
    }

    /**
     * 处理 SSE 流式错误
     */
    private void handleStreamError(SseEmitter emitter, Exception e) {
        String errorMessage = getUserFriendlyError(e);
        try {
            String data = String.format("{\"type\":\"error\",\"content\":\"%s\"}", escapeJson(errorMessage));
            emitter.send(SseEmitter.event().data(data));
            emitter.complete();
        } catch (Exception ex) {
            log.debug("SSE发送错误失败");
        }
    }

    /**
     * 获取用户友好的错误消息
     */
    private String getUserFriendlyError(Exception e) {
        if (e instanceof BusinessException) {
            return e.getMessage();
        }
        if (e.getClass().getSimpleName().contains("Timeout")) {
            return "请求超时，请稍后再试";
        }
        return "系统处理时遇到问题，请稍后再试";
    }

    /**
     * JSON 字符串转义
     */
    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String resolveContextKey(String sessionId, HttpServletRequest request) {
        String authenticatedUserId = getAuthenticatedUserId();
        if (authenticatedUserId != null) {
            return authenticatedUserId;
        }

        if (sessionId != null && !sessionId.isBlank()) {
            return "anon_sess_" + normalizeSessionId(sessionId);
        }

        String httpSessionId = request.getSession(true).getId();
        return "anon_http_" + normalizeSessionId(httpSessionId);
    }

    private String normalizeSessionId(String sessionId) {
        String trimmed = sessionId == null ? "" : sessionId.trim();
        if (trimmed.isEmpty()) {
            return "default";
        }

        StringBuilder normalized = new StringBuilder(trimmed.length());
        for (char ch : trimmed.toCharArray()) {
            if (Character.isLetterOrDigit(ch) || ch == '_' || ch == '-') {
                normalized.append(ch);
            } else {
                normalized.append('_');
            }
        }
        return normalized.length() == 0 ? "default" : normalized.toString();
    }

    /**
     * 获取当前登录用户ID
     */
    private String getAuthenticatedUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof LoginUser loginUser) {
            return loginUser.getUserId().toString();
        }
        return null;
    }
}

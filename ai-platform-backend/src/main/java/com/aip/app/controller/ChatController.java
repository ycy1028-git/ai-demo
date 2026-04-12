package com.aip.app.controller;

import com.aip.app.dto.ChatRequestDTO;
import com.aip.app.dto.ChatResponseDTO;
import com.aip.app.entity.ChatMessage;
import com.aip.app.entity.ChatSession;
import com.aip.app.service.IChatService;
import com.aip.common.result.Result;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 对话控制器（应用层）
 * 注意：与 AppChatController 的 /api/app/chat 区分开
 */
@Slf4j
@RestController
@RequestMapping("/app/conversation")
public class ChatController {

    @Autowired
    private IChatService chatService;

    /**
     * 发送消息
     */
    @PostMapping("/send")
    public Result<ChatResponseDTO> sendMessage(@Valid @RequestBody ChatRequestDTO request) {
        // 从请求头或上下文获取用户ID，使用默认值
        String userId = "00000000000000000000000000000001";
        return Result.ok(chatService.chat(request, userId));
    }

    /**
     * 创建新会话
     */
    @PostMapping("/session")
    public Result<ChatSession> createSession(@RequestParam String assistantCode) {
        // 从请求头或上下文获取用户ID，使用默认值
        String userId = "00000000000000000000000000000001";
        return Result.ok(chatService.createSession(userId, null, assistantCode));
    }

    /**
     * 获取会话详情
     */
    @GetMapping("/session/{sessionId}")
    public Result<ChatSession> getSession(@PathVariable String sessionId) {
        return Result.ok(chatService.getSession(sessionId));
    }

    /**
     * 获取用户的会话列表
     */
    @GetMapping("/sessions")
    public Result<List<ChatSession>> getUserSessions(@RequestParam(required = false) String userId) {
        if (userId == null || userId.isBlank()) {
            userId = "00000000000000000000000000000001"; // 默认值
        }
        return Result.ok(chatService.getUserSessions(userId));
    }

    /**
     * 分页获取用户的会话列表
     */
    @GetMapping("/sessions/page")
    public Result<Page<ChatSession>> getUserSessionsPage(
            @RequestParam(required = false) String userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        if (userId == null || userId.isBlank()) {
            userId = "00000000000000000000000000000001"; // 默认值
        }
        return Result.ok(chatService.getUserSessions(userId, page, size));
    }

    /**
     * 获取会话的消息列表
     */
    @GetMapping("/session/{sessionId}/messages")
    public Result<List<ChatMessage>> getSessionMessages(@PathVariable String sessionId) {
        return Result.ok(chatService.getSessionMessages(sessionId));
    }

    /**
     * 分页获取会话消息
     */
    @GetMapping("/session/{sessionId}/messages/page")
    public Result<Page<ChatMessage>> getSessionMessagesPage(
            @PathVariable String sessionId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(chatService.getSessionMessages(sessionId, page, size));
    }

    /**
     * 归档会话
     */
    @PutMapping("/session/{sessionId}/archive")
    public Result<Void> archiveSession(@PathVariable String sessionId) {
        chatService.archiveSession(sessionId);
        return Result.ok();
    }

    /**
     * 删除会话
     */
    @DeleteMapping("/session/{sessionId}")
    public Result<Void> deleteSession(@PathVariable String sessionId) {
        chatService.deleteSession(sessionId);
        return Result.ok();
    }
}

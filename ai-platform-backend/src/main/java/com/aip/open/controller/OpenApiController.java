package com.aip.open.controller;

import com.aip.common.exception.BusinessException;
import com.aip.common.result.Result;
import com.aip.common.util.UuidV7Utils;
import com.aip.open.dto.*;
import com.aip.open.service.AuthResult;
import com.aip.open.service.OpenApiAuthService;
import com.aip.open.service.RateLimitService;
import com.aip.system.entity.SysApiCredential;
import com.aip.system.mapper.SysApiCredentialMapper;
import com.aip.system.service.ISysApiCredentialService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 开放API控制器
 * 为外部系统提供AI能力调用接口
 */
@Slf4j
@RestController
@RequestMapping("/open/v1")
@RequiredArgsConstructor
public class OpenApiController {

    private final OpenApiAuthService authService;
    private final RateLimitService rateLimitService;
    private final ISysApiCredentialService credentialService;
    private final SysApiCredentialMapper credentialMapper;

    private final ExecutorService executor = Executors.newCachedThreadPool();

    // ========== AI对话 ==========

    /**
     * AI对话（统一入口）
     * POST /open/v1/chat/completions
     */
    @PostMapping("/chat/completions")
    public Result<?> chat(@RequestHeader("Authorization") String authHeader,
                         @RequestBody OpenChatRequest request) {
        // 1. 解析并验证凭证
        AuthResult authResult = parseAndAuthenticate(authHeader);
        if (!authResult.isSuccess()) {
            return Result.fail(401, authResult.getMessage());
        }

        SysApiCredential credential = credentialService.findByApiKey(authResult.getAppId())
                .orElseThrow(() -> new BusinessException("凭证不存在"));

        // 2. 流量控制检查
        RateLimitService.RateLimitResult limitResult = rateLimitService.checkAll(
                credential.getId(),
                credential.getRateLimitQps(),
                credential.getDailyQuota(),
                credential.getTodayCalls(),
                credential.getMonthlyQuota(),
                credential.getMonthlyCalls()
        );

        if (!limitResult.isAllowed()) {
            return Result.fail(429, limitResult.getMessage());
        }

        // 3. 调用AI服务（使用默认模型）
        String response = callAiService(request, credential);

        // 4. 更新调用统计
        updateCallStats(credential);

        // 5. 返回结果
        OpenChatResponse chatResponse = OpenChatResponse.builder()
                .id("msg_" + UuidV7Utils.generateUuidV7String().substring(0, 16))
                .content(response)
                .finishReason("stop")
                .usage(OpenChatResponse.Usage.builder()
                        .promptTokens(estimateTokens(request.getMessages()))
                        .completionTokens(estimateTokens(response))
                        .totalTokens(estimateTokens(request.getMessages()) + estimateTokens(response))
                        .build())
                .created(System.currentTimeMillis() / 1000)
                .build();

        return Result.ok(chatResponse);
    }

    /**
     * AI对话（SSE流式）
     */
    @PostMapping(value = "/chat/completions/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestHeader("Authorization") String authHeader,
                                 @RequestBody OpenChatRequest request) {
        // 1. 鉴权
        AuthResult authResult = parseAndAuthenticate(authHeader);
        if (!authResult.isSuccess()) {
            throw new BusinessException(authResult.getMessage());
        }

        SysApiCredential credential = credentialService.findByApiKey(authResult.getAppId())
                .orElseThrow(() -> new BusinessException("凭证不存在"));

        // 2. 流量控制
        RateLimitService.RateLimitResult limitResult = rateLimitService.checkAll(
                credential.getId(),
                credential.getRateLimitQps(),
                credential.getDailyQuota(),
                credential.getTodayCalls(),
                credential.getMonthlyQuota(),
                credential.getMonthlyCalls()
        );

        if (!limitResult.isAllowed()) {
            throw new BusinessException(limitResult.getMessage());
        }

        // 3. 创建SSEEmitter
        SseEmitter emitter = new SseEmitter(120_000L); // 2分钟超时

        // 4. 异步处理流式响应
        executor.execute(() -> {
            try {
                String response = callAiService(request, credential);

                // 模拟流式输出
                for (char c : response.toCharArray()) {
                    emitter.send(SseEmitter.event()
                            .name("message")
                            .data("data: " + "{\"content\":\"" + c + "\",\"id\":\"msg_xxx\"}\n\n"));
                    Thread.sleep(20);
                }

                emitter.send(SseEmitter.event()
                        .name("done")
                        .data("data: [DONE]\n\n"));

                emitter.complete();
                updateCallStats(credential);

            } catch (Exception e) {
                log.error("流式对话异常", e);
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    // ========== 知识库检索 ==========

    /**
     * 知识库检索
     */
    @PostMapping("/knowledge/search")
    public Result<?> searchKnowledge(@RequestHeader("Authorization") String authHeader,
                                     @RequestBody KnowledgeSearchRequest request) {
        // 1. 鉴权
        AuthResult authResult = parseAndAuthenticate(authHeader);
        if (!authResult.isSuccess()) {
            return Result.fail(401, authResult.getMessage());
        }

        SysApiCredential credential = credentialService.findByApiKey(authResult.getAppId())
                .orElseThrow(() -> new BusinessException("凭证不存在"));

        // 2. 流量控制
        RateLimitService.RateLimitResult limitResult = rateLimitService.checkAll(
                credential.getId(),
                credential.getRateLimitQps(),
                credential.getDailyQuota(),
                credential.getTodayCalls(),
                credential.getMonthlyQuota(),
                credential.getMonthlyCalls()
        );

        if (!limitResult.isAllowed()) {
            return Result.fail(429, limitResult.getMessage());
        }

        // 3. 执行检索（TODO: 集成知识库服务）
        // 模拟返回空结果
        KnowledgeSearchResponse response = KnowledgeSearchResponse.builder()
                .results(List.of())
                .build();

        // 4. 更新统计
        updateCallStats(credential);

        return Result.ok(response);
    }

    // ========== 嵌入配置 ==========

    /**
     * 获取嵌入配置
     */
    @GetMapping("/widget/config")
    public Result<?> getWidgetConfig(@RequestParam String appId) {
        // 1. 查找凭证
        Optional<SysApiCredential> credentialOpt = credentialService.findByAppId(appId);
        if (credentialOpt.isEmpty()) {
            return Result.fail(404, "应用不存在");
        }

        SysApiCredential credential = credentialOpt.get();

        // 2. 检查状态
        if (!credential.isValid()) {
            return Result.fail(401, "应用未授权或已过期");
        }

        // 3. 生成Widget Token
        String widgetToken = "wt_" + UuidV7Utils.generateUuidV7String();

        WidgetConfigResponse response = WidgetConfigResponse.builder()
                .widgetToken(widgetToken)
                .serverUrl("wss://" + getServerHost() + "/open/v1/widget/connect")
                .theme(WidgetConfigResponse.ThemeConfig.builder()
                        .primaryColor("#1890ff")
                        .position("right")
                        .zIndex(9999)
                        .offset(WidgetConfigResponse.Offset.builder().x(20).y(100).build())
                        .build())
                .build();

        return Result.ok(response);
    }

    // ========== 私有方法 ==========

    /**
     * 解析Authorization头并鉴权
     */
    private AuthResult parseAndAuthenticate(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return AuthResult.fail("Authorization格式错误，应为: Bearer <api_key>:<api_secret>");
        }

        String[] parts = authHeader.substring(7).split(":", 2);
        if (parts.length != 2) {
            return AuthResult.fail("Authorization格式错误，应为: Bearer <api_key>:<api_secret>");
        }

        String apiKey = parts[0];
        String apiSecret = parts[1];

        return authService.authenticate(apiKey, apiSecret);
    }

    /**
     * 调用AI服务（使用默认模型）
     */
    private String callAiService(OpenChatRequest request, SysApiCredential credential) {
        log.info("调用AI服务: credential={}", credential.getAppId());

        String userMessage = request.getMessages() != null && !request.getMessages().isEmpty()
                ? request.getMessages().get(request.getMessages().size() - 1).getContent()
                : "";

        return "您好！我是AI助手。这是您的消息: " + userMessage + "。\n" +
               "当前使用默认模型为您服务。";
    }

    /**
     * 更新调用统计
     */
    private void updateCallStats(SysApiCredential credential) {
        try {
            credentialMapper.incrementCalls(credential.getId(), Instant.now());
        } catch (Exception e) {
            log.error("更新调用统计失败", e);
        }
    }

    /**
     * 估算token数量（简化版）
     */
    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return text.length() * 2;
    }

    private int estimateTokens(List<OpenChatRequest.ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (OpenChatRequest.ChatMessage msg : messages) {
            total += estimateTokens(msg.getContent());
        }
        return total;
    }

    /**
     * 获取服务器地址
     */
    private String getServerHost() {
        return "ai.example.com";
    }
}

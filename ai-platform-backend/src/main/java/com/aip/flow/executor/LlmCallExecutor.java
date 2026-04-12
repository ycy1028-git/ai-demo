
package com.aip.flow.executor;

import com.aip.ai.entity.AiModelConfig;
import com.aip.ai.service.IAiModelConfigService;
import com.aip.flow.engine.FlowContext;
import com.aip.flow.engine.NodeResult;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM 调用节点执行器
 * 调用 AI 大语言模型，根据上下文和系统提示生成智能回复
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmCallExecutor extends BaseNodeExecutor {

    private final IAiModelConfigService aiModelConfigService;
    private static final String CONFIG_KEY_MODEL_ID = "modelId";
    private static final String CONFIG_KEY_MODEL_NAME = "modelName";

    @PostConstruct
    public void init() {
        initBase("llm_call", "AI 对话", "调用 AI 大模型，根据上下文和系统提示生成智能回复", "ai",
                Arrays.asList("回答", "解释", "说明", "描述", "回复", "生成"));
    }

    @Override
    public NodeResult execute(FlowContext context, Map<String, Object> config) {
        try {
            AiModelConfig modelConfig = getModelConfig(config);
            if (modelConfig == null) {
                return NodeResult.fail("未找到可用的 AI 模型配置，请联系管理员", "MODEL_NOT_FOUND");
            }

            String systemPrompt = (String) config.getOrDefault("systemPrompt",
                    "你是一个友好的 AI 助手，请根据用户的问题给出有帮助、准确的回答。");
            double temperature = modelConfig.getTemperature() != null
                    ? modelConfig.getTemperature().doubleValue()
                    : 0.7;

            List<Map<String, String>> messages = buildMessages(context, systemPrompt);
            String response = callLlm(messages, modelConfig, temperature);

            Map<String, Object> params = new HashMap<>();
            params.put("llm_response", response);
            params.put("llm_model", modelConfig.getModelName());
            params.put("llm_provider", modelConfig.getProvider());

            log.info("LLM 调用成功: model={}, provider={}, responseLength={}",
                    modelConfig.getModelName(), modelConfig.getProvider(), response.length());
            return NodeResult.success(response, params);

        } catch (Exception e) {
            log.error("LLM 调用异常: {}", e.getMessage(), e);
            return NodeResult.fail("AI 服务暂时繁忙，请稍后再试: " + e.getMessage(), "LLM_ERROR");
        }
    }

    public NodeResult executeStreaming(FlowContext context, Map<String, Object> config, Consumer<String> chunkCallback) {
        try {
            AiModelConfig modelConfig = getModelConfig(config);
            if (modelConfig == null) {
                chunkCallback.accept("错误：未找到可用的 AI 模型配置");
                return NodeResult.fail("未找到可用的 AI 模型配置", "MODEL_NOT_FOUND");
            }

            String systemPrompt = (String) config.getOrDefault("systemPrompt",
                    "你是一个友好的 AI 助手，请根据用户的问题给出有帮助、准确的回答。");
            double temperature = modelConfig.getTemperature() != null
                    ? modelConfig.getTemperature().doubleValue()
                    : 0.7;

            List<Map<String, String>> messages = buildMessages(context, systemPrompt);
            String fullResponse = callLlmStreaming(messages, modelConfig, temperature, chunkCallback);

            Map<String, Object> params = new HashMap<>();
            params.put("llm_response", fullResponse);
            params.put("llm_model", modelConfig.getModelName());
            params.put("llm_provider", modelConfig.getProvider());

            log.info("LLM 流式调用成功: model={}, responseLength={}",
                    modelConfig.getModelName(), fullResponse.length());

            return NodeResult.success(fullResponse, params);

        } catch (Exception e) {
            log.error("LLM 流式调用异常: {}", e.getMessage(), e);
            chunkCallback.accept("错误：AI 服务暂时繁忙，请稍后再试");
            return NodeResult.fail("AI 服务暂时繁忙: " + e.getMessage(), "LLM_ERROR");
        }
    }

    private AiModelConfig getModelConfig(Map<String, Object> config) {
        if (config.containsKey(CONFIG_KEY_MODEL_ID) && config.get(CONFIG_KEY_MODEL_ID) != null) {
            String modelId = config.get(CONFIG_KEY_MODEL_ID).toString();
            try {
                return aiModelConfigService.getModelById(modelId);
            } catch (Exception e) {
                log.warn("通过 modelId 获取模型配置失败: {}", modelId);
            }
        }

        if (config.containsKey(CONFIG_KEY_MODEL_NAME) && config.get(CONFIG_KEY_MODEL_NAME) != null) {
            String modelName = config.get(CONFIG_KEY_MODEL_NAME).toString();
            return aiModelConfigService.listAll().stream()
                    .filter(m -> m.getModelName().equalsIgnoreCase(modelName) && m.getEnabled())
                    .findFirst()
                    .map(m -> aiModelConfigService.getModelById(m.getId()))
                    .orElse(null);
        }

        return aiModelConfigService.getDefaultModel();
    }

    private List<Map<String, String>> buildMessages(FlowContext context, String systemPrompt) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));

        if (context.getHistory() != null) {
            for (String historyMsg : context.getHistory()) {
                if (historyMsg.startsWith("用户:") || historyMsg.startsWith("User:")) {
                    messages.add(Map.of("role", "user", "content", historyMsg.replaceFirst("^.{2,5}:\\s*", "")));
                } else if (historyMsg.startsWith("AI:") || historyMsg.startsWith("助手:")) {
                    messages.add(Map.of("role", "assistant", "content", historyMsg.replaceFirst("^.{2,5}:\\s*", "")));
                }
            }
        }

        messages.add(Map.of("role", "user", "content", context.getCurrentMessage()));
        return messages;
    }

    private String callLlm(List<Map<String, String>> messages, AiModelConfig modelConfig, double temperature) {
        String apiKey = modelConfig.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            String lastMessage = messages.get(messages.size() - 1).get("content");
            return String.format("【%s 模型回复】您的消息「%s」已收到。\n\n当前使用模型: %s (%s)\n提示: 请在模型配置中设置 API 密钥。",
                    modelConfig.getProvider(), lastMessage.substring(0, Math.min(20, lastMessage.length())),
                    modelConfig.getName(), modelConfig.getModelName());
        }

        try {
            String apiUrl = modelConfig.getFullApiUrl();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", modelConfig.getModelName());
            requestBody.put("messages", messages);
            requestBody.put("temperature", temperature);
            if (modelConfig.getMaxTokens() != null) {
                requestBody.put("max_tokens", modelConfig.getMaxTokens());
            }

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<Map> response = restTemplate.exchange(apiUrl, HttpMethod.POST, entity, Map.class);

            if (response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                    if (message != null) {
                        return (String) message.get("content");
                    }
                }
            }
            return "抱歉，AI 响应格式解析失败。";
        } catch (Exception e) {
            log.error("LLM API 调用失败: {}", e.getMessage(), e);
            return "抱歉，调用 AI 服务时出错：" + e.getMessage();
        }
    }

    private String callLlmStreaming(List<Map<String, String>> messages, AiModelConfig modelConfig,
                                     double temperature, Consumer<String> chunkCallback) {
        String apiKey = modelConfig.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            String lastMessage = messages.get(messages.size() - 1).get("content");
            String response = String.format("【%s 模型回复】您的消息「%s」已收到。\n\n当前使用模型: %s (%s)\n提示: 请在模型配置中设置 API 密钥。",
                    modelConfig.getProvider(), lastMessage.substring(0, Math.min(20, lastMessage.length())),
                    modelConfig.getName(), modelConfig.getModelName());
            simulateTypingEffect(response, chunkCallback);
            return response;
        }

        try {
            String apiUrl = modelConfig.getFullApiUrl();
            log.info("开始流式调用: url={}, model={}", apiUrl, modelConfig.getModelName());

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", modelConfig.getModelName());
            requestBody.put("messages", messages);
            requestBody.put("temperature", temperature);
            if (modelConfig.getMaxTokens() != null) {
                requestBody.put("max_tokens", modelConfig.getMaxTokens());
            }
            requestBody.put("stream", true);

            WebClient webClient = WebClient.builder()
                    .baseUrl(apiUrl)
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .defaultHeader(HttpHeaders.ACCEPT, "text/event-stream")
                    .build();

            StringBuilder fullContent = new StringBuilder();
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<String> errorRef = new AtomicReference<>();

            webClient.post()
                    .bodyValue(requestBody)
                    .exchangeToFlux(clientResponse -> {
                        log.info("收到响应状态: {}", clientResponse.statusCode());
                        if (clientResponse.statusCode().isError()) {
                            errorRef.set("API 返回错误: " + clientResponse.statusCode());
                            latch.countDown();
                            return Flux.empty();
                        }
                        return clientResponse.bodyToFlux(String.class);
                    })
                    .doOnError(e -> {
                        log.error("流式读取错误: {}", e.getMessage());
                        errorRef.set(e.getMessage());
                        latch.countDown();
                    })
                    .doOnComplete(() -> {
                        log.info("流式读取完成");
                        latch.countDown();
                    })
                    .subscribe(chunk -> {
                        log.debug("收到 chunk: {}", chunk);
                        String content = parseSSEContent(chunk);
                        if (content != null && !content.isEmpty()) {
                            fullContent.append(content);
                            chunkCallback.accept(content);
                        }
                    });

            // 等待流式响应完成，最多60秒
            boolean completed = latch.await(60, TimeUnit.SECONDS);
            if (!completed) {
                log.warn("流式调用超时");
            }

            if (errorRef.get() != null) {
                throw new RuntimeException(errorRef.get());
            }

            if (fullContent.length() == 0) {
                log.warn("流式响应为空");
                String fallback = "抱歉，AI 响应为空。";
                chunkCallback.accept(fallback);
                return fallback;
            }

            log.info("流式调用成功，内容长度: {}", fullContent.length());
            return fullContent.toString();

        } catch (Exception e) {
            log.error("LLM 流式 API 调用失败: {}", e.getMessage(), e);
            String errorMsg = "抱歉，调用 AI 服务时出错：" + e.getMessage();
            chunkCallback.accept(errorMsg);
            return errorMsg;
        }
    }

    private String parseSSEContent(String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return null;
        }

        String json = chunk.trim();

        // 处理 SSE 格式的结束标记
        if (json.startsWith("data: [DONE]") || json.equals("data: [DONE]") || json.equals("[DONE]")) {
            return null;
        }

        // 如果是 SSE 格式（data: 开头），去掉前缀
        if (json.startsWith("data:")) {
            json = json.substring(5).trim();
        }

        // 如果是 [DONE] 格式
        if (json.equals("[DONE]")) {
            return null;
        }

        // 必须是 JSON 对象
        if (!json.startsWith("{")) {
            return null;
        }

        // 千问/阿里云格式: {"choices":[{"delta":{"content":"xxx"}}...]}
        String content1 = extractJsonValue(json, "choices", "delta", "content");
        if (content1 != null && !content1.isEmpty()) {
            return content1;
        }

        // 格式2: {"choices":[{"delta":{"role":"assistant","content":"xxx"}}...]}
        String content2 = extractJsonValue(json, "choices", "delta", "content");
        if (content2 != null && !content2.isEmpty()) {
            return content2;
        }

        // 格式3: {"content":"xxx"}
        String content3 = extractJsonValue(json, "content");
        if (content3 != null && !content3.isEmpty()) {
            return content3;
        }

        // 格式4: {"choices":[{"message":{"content":"xxx"}}...]}
        String content4 = extractJsonValue(json, "choices", "message", "content");
        if (content4 != null && !content4.isEmpty()) {
            return content4;
        }

        return null;
    }

    /**
     * 从 JSON 中提取嵌套路径的值
     */
    private String extractJsonValue(String json, String... keys) {
        try {
            // 直接用正则匹配最后的键值对
            String lastKey = keys[keys.length - 1];
            Pattern p = Pattern.compile("\"\\s*" + lastKey + "\"\\s*:\\s*\"([^\"\\\\]*(?:\\\\.[^\"\\\\]*)*)\"");
            Matcher m = p.matcher(json);
            if (m.find()) {
                String value = m.group(1);
                return value.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\");
            }
        } catch (Exception e) {
            log.debug("JSON 解析失败: {}", e.getMessage());
        }
        return null;
    }

    private void simulateTypingEffect(String text, Consumer<String> callback) {
        String[] words = text.split("(?<=[，。！？；：、\n])|(?=[，。！？；：、\n])|(?<=\\s)|(?=\\s)");
        for (String word : words) {
            if (!word.isEmpty()) {
                callback.accept(word);
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }
}

package com.aip.ai.service.impl;

import com.aip.ai.entity.AiModelConfig;
import com.aip.ai.service.IAiModelService;
import com.aip.ai.service.IAiModelConfigService;
import com.aip.common.utils.ApiKeyEncryptUtil;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 通用AI模型服务实现
 * 支持 OpenAI 兼容格式的 API 调用
 */
@Slf4j
@Service
public class AiModelServiceImpl implements IAiModelService {

    private final IAiModelConfigService aiModelConfigService;

    public AiModelServiceImpl(IAiModelConfigService aiModelConfigService) {
        this.aiModelConfigService = aiModelConfigService;
    }

    @Override
    public List<AiModelConfig> getAvailableModels() {
        return aiModelConfigService.listEnabled().stream()
                .map(vo -> {
                    AiModelConfig config = new AiModelConfig();
                    config.setId(vo.getId());
                    config.setName(vo.getName());
                    config.setProvider(vo.getProvider());
                    config.setApiUrl(vo.getApiUrl());
                    config.setModelName(vo.getModelName());
                    config.setTemperature(vo.getTemperature());
                    config.setMaxTokens(vo.getMaxTokens());
                    config.setEnabled(vo.getEnabled());
                    config.setIsDefault(vo.getIsDefault());
                    return config;
                })
                .toList();
    }

    @Override
    public AiModelConfig getModelConfig(String modelId) {
        return aiModelConfigService.getModelById(modelId);
    }

    @Override
    public AiModelConfig getDefaultModel() {
        return aiModelConfigService.getDefaultModel();
    }

    @Override
    public String chat(String modelId, List<Message> messages) {
        StringBuilder fullContent = new StringBuilder();
        AtomicBoolean completed = new AtomicBoolean(false);

        streamChat(modelId, messages,
                fullContent::append,
                () -> completed.set(true),
                error -> { throw new RuntimeException(error); }
        );

        // 等待完成
        int waitCount = 0;
        while (!completed.get() && waitCount < 300) {
            try {
                Thread.sleep(100);
                waitCount++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        return fullContent.toString();
    }

    @Override
    public String chatWithDefault(List<Message> messages) {
        StringBuilder fullContent = new StringBuilder();
        AtomicBoolean completed = new AtomicBoolean(false);

        streamChatWithDefault(messages,
                fullContent::append,
                () -> completed.set(true),
                error -> { throw new RuntimeException(error); }
        );

        // 等待完成
        int waitCount = 0;
        while (!completed.get() && waitCount < 300) {
            try {
                Thread.sleep(100);
                waitCount++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        return fullContent.toString();
    }

    @Override
    public void streamChat(String modelId, List<Message> messages,
                          Consumer<String> onChunk, Runnable onComplete, Consumer<String> onError) {
        AiModelConfig config = getModelConfig(modelId);
        streamChat(config, messages, onChunk, onComplete, onError);
    }

    @Override
    public void streamChatWithDefault(List<Message> messages,
                                     Consumer<String> onChunk, Runnable onComplete, Consumer<String> onError) {
        AiModelConfig config = getDefaultModel();
        streamChat(config, messages, onChunk, onComplete, onError);
    }

    /**
     * 流式调用AI模型
     */
    private void streamChat(AiModelConfig config, List<Message> messages,
                            Consumer<String> onChunk, Runnable onComplete, Consumer<String> onError) {
        AtomicBoolean completed = new AtomicBoolean(false);

        try {
            String apiUrl = config.getFullApiUrl();
            // 解密 API Key
            String apiKey = ApiKeyEncryptUtil.decrypt(config.getApiKey());

            log.info("调用AI模型: provider={}, model={}, url={}", config.getProvider(), config.getModelName(), apiUrl);

            // 构建请求体
            String requestBody = buildRequestBody(config, messages, true);

            // 构建HTTP请求
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(apiUrl).openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setUseCaches(false);
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(120000);

            // 设置请求头
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setRequestProperty("X-DashScope-SSE", "disable");

            // 发送请求体
            try (var os = conn.getOutputStream()) {
                os.write(requestBody.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            // 获取响应
            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                String errorMsg = "API请求失败，响应码: " + responseCode;
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                    String line;
                    StringBuilder errorBody = new StringBuilder();
                    while ((line = reader.readLine()) != null) {
                        errorBody.append(line);
                    }
                    if (errorBody.length() > 0) {
                        errorMsg += "，响应内容: " + errorBody;
                    }
                }
                log.error("AI API 调用失败: {}", errorMsg);
                onError.accept(errorMsg);
                return;
            }

            // 处理SSE流式响应
            StringBuilder fullContent = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null && !completed.get()) {
                    // SSE格式: data: {...}
                    if (line.startsWith("data:")) {
                        String jsonStr = line.substring(5).trim();
                        if (jsonStr.isEmpty() || jsonStr.equals("[DONE]")) {
                            continue;
                        }

                        try {
                            StreamResponse response = parseStreamResponse(jsonStr);
                            if (response != null && response.getChoices() != null
                                    && !response.getChoices().isEmpty()) {
                                StreamResponse.Delta delta = response.getChoices().get(0).getDelta();
                                if (delta != null && delta.getContent() != null) {
                                    String chunk = delta.getContent();
                                    fullContent.append(chunk);
                                    onChunk.accept(chunk);
                                }

                                // 检查是否完成
                                String finishReason = response.getChoices().get(0).getFinishReason();
                                if ("stop".equals(finishReason)) {
                                    completed.set(true);
                                    break;
                                }
                            }
                        } catch (Exception e) {
                            log.warn("解析SSE数据块失败: {}, data: {}", e.getMessage(), jsonStr);
                        }
                    }
                }
            }

            if (!completed.get()) {
                completed.set(true);
            }

            onComplete.run();

        } catch (Exception e) {
            log.error("AI API 流式调用异常", e);
            onError.accept("API调用异常: " + e.getMessage());
        }
    }

    /**
     * 构建请求体
     */
    private String buildRequestBody(AiModelConfig config, List<Message> messages, boolean stream) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"model\": \"").append(config.getModelName()).append("\",");
        sb.append("\"stream\": ").append(stream).append(",");
        sb.append("\"temperature\": ").append(config.getTemperature()).append(",");
        sb.append("\"max_tokens\": ").append(config.getMaxTokens()).append(",");
        sb.append("\"messages\": [");
        for (int i = 0; i < messages.size(); i++) {
            Message msg = messages.get(i);
            sb.append("{");
            sb.append("\"role\": \"").append(msg.getRole()).append("\",");
            sb.append("\"content\": \"").append(escapeJson(msg.getContent())).append("\"");
            if (msg.getName() != null) {
                sb.append(", \"name\": \"").append(msg.getName()).append("\"");
            }
            sb.append("}");
            if (i < messages.size() - 1) {
                sb.append(",");
            }
        }
        sb.append("]");
        sb.append("}");
        return sb.toString();
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

    /**
     * 解析SSE流式响应
     */
    private StreamResponse parseStreamResponse(String json) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(json, StreamResponse.class);
        } catch (Exception e) {
            log.warn("解析响应JSON失败: {}", e.getMessage());
            return null;
        }
    }

    // ==================== 内部类 ====================

    /**
     * 流式响应结构（对应OpenAI兼容格式）
     */
    @Data
    public static class StreamResponse {
        private String id;
        private String object;
        private long created;
        private String model;
        private List<Choice> choices;

        @Data
        public static class Choice {
            private int index;
            private Delta delta;
            private String finishReason;
        }

        @Data
        public static class Delta {
            private String role;
            private String content;
        }
    }
}

package com.aip.common.ai;

import com.aip.ai.entity.AiModelConfig;
import com.aip.common.utils.ApiKeyEncryptUtil;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 统一 LLM API 服务
 * 支持多种模型提供商：通义千问、DeepSeek、智谱GLM、OpenAI 等
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UnifiedLlmService {

    /**
     * 测试模型连接
     * @param config 模型配置
     * @return 测试结果描述
     */
    public String testConnection(AiModelConfig config) {
        String apiUrl = config.getFullApiUrl();
        String apiKey = ApiKeyEncryptUtil.decrypt(config.getApiKey());
        String model = config.getModelName();

        try {
            // ���建简单的测试消息
            String requestBody = buildRequestBody(model,
                    List.of(new Message("user", "你好，请回复OK")), false, 0.7, 100);

            HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setUseCaches(false);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(30000);

            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);

            // 通义千问特殊处理
            if ("qwen".equalsIgnoreCase(config.getProvider()) || "dashscope".equalsIgnoreCase(config.getProvider())) {
                conn.setRequestProperty("X-DashScope-SSE", "disable");
            }

            try (var os = conn.getOutputStream()) {
                os.write(requestBody.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            int responseCode = conn.getResponseCode();
            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
            }

            if (responseCode == 200) {
                // 尝试解析响应判断是否成功
                if (response.length() > 0) {
                    return "API响应正常";
                }
                return "连接成功";
            } else {
                return "API响应码: " + responseCode + "，响应: " + response;
            }

        } catch (Exception e) {
            throw new RuntimeException("连接失败: " + e.getMessage());
        }
    }

    /**
     * 流式调用 LLM
     *
     * @param config     模型配置
     * @param messages   对话消息列表
     * @param onChunk    每个chunk的回调
     * @param onComplete 完成时的回调
     * @param onError    错误时的回调
     */
    public void streamChat(AiModelConfig config, List<Message> messages,
                           Consumer<String> onChunk, Runnable onComplete, Consumer<String> onError) {
        AtomicBoolean completed = new AtomicBoolean(false);
        String apiUrl = config.getFullApiUrl();
        String apiKey = ApiKeyEncryptUtil.decrypt(config.getApiKey());
        String model = config.getModelName();
        Double temperature = config.getTemperature() != null ? config.getTemperature().doubleValue() : 0.7;
        Integer maxTokens = config.getMaxTokens();

        try {
            String requestBody = buildRequestBody(model, messages, true, temperature, maxTokens);

            HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setUseCaches(false);
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(120000);

            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);

            // 通义千问特殊处理
            if ("qwen".equalsIgnoreCase(config.getProvider()) || "dashscope".equalsIgnoreCase(config.getProvider())) {
                conn.setRequestProperty("X-DashScope-SSE", "disable");
            }

            try (var os = conn.getOutputStream()) {
                os.write(requestBody.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

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
                log.error("LLM API 调用失败: {}", errorMsg);
                onError.accept(errorMsg);
                return;
            }

            StringBuilder fullContent = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null && !completed.get()) {
                    if (line.startsWith("data:")) {
                        String jsonStr = line.substring(5).trim();
                        if (jsonStr.isEmpty() || "[DONE]".equals(jsonStr)) {
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
            log.error("LLM API 流式调用异常", e);
            onError.accept("API调用异常: " + e.getMessage());
        }
    }

    /**
     * 非流式调用 LLM
     */
    public String chat(AiModelConfig config, List<Message> messages) {
        StringBuilder fullContent = new StringBuilder();
        AtomicBoolean completed = new AtomicBoolean(false);

        streamChat(config, messages,
                fullContent::append,
                () -> completed.set(true),
                error -> { throw new RuntimeException(error); }
        );

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

    private String buildRequestBody(String model, List<Message> messages, boolean stream,
                                    Double temperature, Integer maxTokens) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"model\": \"").append(model).append("\",");
        sb.append("\"stream\": ").append(stream).append(",");
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
        if (temperature != null) {
            sb.append(", \"temperature\": ").append(temperature);
        }
        if (maxTokens != null) {
            sb.append(", \"max_tokens\": ").append(maxTokens);
        }
        sb.append("}");
        return sb.toString();
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private StreamResponse parseStreamResponse(String json) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            return mapper.readValue(json, StreamResponse.class);
        } catch (Exception e) {
            log.warn("解析响应JSON失败: {}", e.getMessage());
            return null;
        }
    }

    @Data
    public static class Message {
        private String role;
        private String content;
        private String name;

        public Message() {}

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    @Data
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public static class StreamResponse {
        private String id;
        private String object;
        private long created;
        private String model;
        private List<Choice> choices;
        private Object usage; // 允许 usage 字段存在但不需要解析

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

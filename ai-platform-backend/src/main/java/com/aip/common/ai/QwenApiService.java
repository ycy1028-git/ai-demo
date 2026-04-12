package com.aip.common.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 阿里通义千问API服务（支持流式输出）
 */
@Slf4j
@Component
public class QwenApiService {

    private static final String API_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";
    private static final String API_KEY_PLACEHOLDER = "${QWEN_API_KEY}";

    /**
     * 流式调用通义千问API
     *
     * @param apiKey        API密钥
     * @param model         模型名称（如 qwen-plus、qwen-turbo、qwen-max 等）
     * @param messages      对话消息列表
     * @param onChunk       每个chunk的回调（流式输出时调用）
     * @param onComplete    完成时的回调
     * @param onError       错误时的回调
     */
    public void streamChat(String apiKey, String model, List<Message> messages,
                          Consumer<String> onChunk, Runnable onComplete, Consumer<String> onError) {
        AtomicBoolean completed = new AtomicBoolean(false);

        try {
            // 构建请求体
            String requestBody = buildRequestBody(model, messages, true);

            // 构建HTTP请求
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(API_URL).openConnection();
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
                log.error("Qwen API 调用失败: {}", errorMsg);
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
            log.error("Qwen API 流式调用异常", e);
            onError.accept("API调用异常: " + e.getMessage());
        }
    }

    /**
     * 非流式调用通义千问API
     *
     * @param apiKey    API密钥
     * @param model     模型名称
     * @param messages  对话消息列表
     * @return 完整的AI回复
     */
    public String chat(String apiKey, String model, List<Message> messages) {
        StringBuilder fullContent = new StringBuilder();
        AtomicBoolean completed = new AtomicBoolean(false);

        streamChat(apiKey, model, messages,
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

    /**
     * 构建请求体
     */
    private String buildRequestBody(String model, List<Message> messages, boolean stream) {
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
     * 对话消息
     */
    @Data
    public static class Message {
        private String role;       // system, user, assistant
        private String content;    // 消息内容
        private String name;       // 可选，消息发送者名称

        public Message() {}

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

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

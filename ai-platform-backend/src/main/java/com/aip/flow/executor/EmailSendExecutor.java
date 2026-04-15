package com.aip.flow.executor;

import com.aip.common.util.MailService;
import com.aip.flow.engine.FlowContext;
import com.aip.flow.engine.NodeResult;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 邮件发送节点执行器
 * 发送邮件给指定用户，支持参数缺失时自动询问
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailSendExecutor extends BaseNodeExecutor {

    private final MailService mailService;

    private static final String PARAM_TO = "to";           // 收件人邮箱
    private static final String PARAM_SUBJECT = "subject"; // 邮件主题
    private static final String PARAM_CONTENT = "content"; // 邮件内容

    private static final String CONFIG_KEY_FROM = "from";         // 发件人（可选，默认配置的发件人）
    private static final String CONFIG_KEY_SUBJECT_PREFIX = "subjectPrefix"; // 主题前缀
    private static final String CONFIG_KEY_REQUIRED = "requiredParams"; // 必需参数列表

    @PostConstruct
    public void init() {
        initBase("email_send", "发送邮件", 
                "发送邮件给指定用户，当参数缺失时会自动询问用户提供", "execute",
                Arrays.asList("发送邮件", "发邮件", "邮箱", "邮件", "发到"));
    }

    @Override
    public NodeResult execute(FlowContext context, Map<String, Object> config) {
        try {
            Map<String, Object> params = context.getParams();
            String currentMessage = context.getCurrentMessage();

            // 尝试从当前消息中提取邮件参数
            extractEmailParamsFromMessage(currentMessage, params);
            fillMissingParamsFromFollowUp(context, params, currentMessage);

            // 检查必需的邮件参数
            String to = getParamValue(params, currentMessage, PARAM_TO, "邮箱");
            String subject = getParamValue(params, currentMessage, PARAM_SUBJECT, "邮件主题");
            String content = getParamValue(params, currentMessage, PARAM_CONTENT, "邮件内容");

            // 逐个检查参数，缺失则询问
            if (to == null || to.isBlank()) {
                return NodeResult.needInput("请问您希望将邮件发送到哪个邮箱地址？");
            }

            if (!isValidEmail(to)) {
                return NodeResult.needInput("您提供的邮箱地址格式不正确，请重新输入有效的邮箱地址");
            }

            if (subject == null || subject.isBlank()) {
                return NodeResult.needInput("请告诉我邮件的主题是什么？");
            }

            if (content == null || content.isBlank()) {
                return NodeResult.needInput("请告诉我邮件的内容是什么？");
            }

            String subjectPrefix = (String) config.getOrDefault(CONFIG_KEY_SUBJECT_PREFIX, "");
            if (!subjectPrefix.isBlank()) {
                subject = subjectPrefix + subject;
            }

            boolean success = mailService.sendSimpleMail(to, subject, content);

            if (success) {
                log.info("邮件发送成功: to={}, subject={}", to, subject);
                return NodeResult.success(
                    String.format("邮件已成功发送到 %s，主题：%s", to, subject),
                    Map.of("email_sent", true, "email_to", to, "email_subject", subject)
                );
            } else {
                return NodeResult.fail("邮件发送失败，请稍后再试或联系管理员", "EMAIL_SEND_FAILED");
            }

        } catch (Exception e) {
            log.error("邮件发送异常: {}", e.getMessage(), e);
            return NodeResult.fail("邮件服务暂时不可用: " + e.getMessage(), "EMAIL_ERROR");
        }
    }

    private void fillMissingParamsFromFollowUp(FlowContext context, Map<String, Object> params, String currentMessage) {
        if (currentMessage == null || currentMessage.isBlank()) {
            return;
        }

        boolean waitingForEmail = "waiting".equalsIgnoreCase(context.getStatus())
                || "email_send".equals(String.valueOf(context.getMetadata("last_node_type")));
        if (!waitingForEmail) {
            return;
        }

        String normalized = currentMessage.trim();
        if (normalized.isBlank()) {
            return;
        }

        if (params.get(PARAM_TO) == null) {
            String extractedEmail = extractEmailFromText(normalized);
            if (extractedEmail != null) {
                params.put(PARAM_TO, extractedEmail);
                return;
            }
        }

        if (params.get(PARAM_SUBJECT) == null) {
            params.put(PARAM_SUBJECT, normalized);
            return;
        }

        if (params.get(PARAM_CONTENT) == null) {
            params.put(PARAM_CONTENT, normalized);
        }
    }

    /**
     * 从用户消息中提取邮件参数
     */
    private void extractEmailParamsFromMessage(String message, Map<String, Object> params) {
        if (message == null || message.isBlank()) {
            return;
        }

        // 提取收件人邮箱
        if (params.get(PARAM_TO) == null) {
            String extractedEmail = extractEmailFromText(message);
            if (extractedEmail != null) {
                params.put(PARAM_TO, extractedEmail);
            }
        }

        // 提取主题（关键词：主题、是、标题）
        if (params.get(PARAM_SUBJECT) == null) {
            String extractedSubject = extractSubjectFromText(message);
            if (extractedSubject != null) {
                params.put(PARAM_SUBJECT, extractedSubject);
            }
        }

        // 提取内容（关键词：内容、是、说）
        if (params.get(PARAM_CONTENT) == null) {
            String extractedContent = extractContentFromText(message);
            if (extractedContent != null) {
                params.put(PARAM_CONTENT, extractedContent);
            }
        }
    }

    /**
     * 从文本中提取邮件主题
     */
    private String extractSubjectFromText(String text) {
        // 匹配模式：主题是XXX、标题是XXX、主题XXX
        String[] patterns = {
            "主题[是为：:]\\s*(.+?)(?:，|,|。|$)",
            "标题[是为：:]\\s*(.+?)(?:，|,|。|$)",
            "subject[:\\s]\\s*(.+?)(?:，|,|。|$)"
        };

        for (String pattern : patterns) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(pattern, java.util.regex.Pattern.CASE_INSENSITIVE).matcher(text);
            if (m.find()) {
                return m.group(1).trim();
            }
        }
        return null;
    }

    /**
     * 从文本中提取邮件内容
     */
    private String extractContentFromText(String text) {
        // 匹配模式：内容是XXX、说XXX、内容XXX
        String[] patterns = {
            "内容[是为：:]\\s*(.+?)(?:，|,|。|$)",
            "说[是为：:]\\s*(.+?)(?:，|,|。|$)",
            "content[:\\s]\\s*(.+?)(?:，|,|。|$)"
        };

        for (String pattern : patterns) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(pattern, java.util.regex.Pattern.CASE_INSENSITIVE).matcher(text);
            if (m.find()) {
                return m.group(1).trim();
            }
        }
        return null;
    }

    /**
     * 获取必需参数列表
     */
    @SuppressWarnings("unchecked")
    private List<String> getRequiredParams(Map<String, Object> config) {
        List<String> required = new ArrayList<>();
        required.add(PARAM_TO);
        required.add(PARAM_SUBJECT);
        required.add(PARAM_CONTENT);

        Object customRequired = config.get(CONFIG_KEY_REQUIRED);
        if (customRequired instanceof List) {
            return (List<String>) customRequired;
        }
        return required;
    }

    /**
     * 检查缺失的参数
     */
    private List<String> checkMissingParams(Map<String, Object> params, List<String> required) {
        List<String> missing = new ArrayList<>();
        for (String param : required) {
            Object value = params.get(param);
            if (value == null || (value instanceof String str && str.isBlank())) {
                missing.add(param);
            }
        }
        return missing;
    }

    /**
     * 处理缺失参数，返回提示用户的消息
     */
    private NodeResult handleMissingParams(List<String> missingParams, 
                                            Map<String, Object> params,
                                            String currentMessage) {
        if (missingParams.contains(PARAM_TO)) {
            return NodeResult.needInput("请问您希望将邮件发送到哪个邮箱地址？");
        }

        if (missingParams.contains(PARAM_SUBJECT)) {
            return NodeResult.needInput("请告诉我邮件的主题是什么？");
        }

        if (missingParams.contains(PARAM_CONTENT)) {
            return NodeResult.needInput("请告诉我邮件的内容是什么？");
        }

        return NodeResult.needInput("请补充必要的邮件信息：" + String.join("、", missingParams));
    }

    /**
     * 尝试从上下文参数或当前消息中提取参数值
     */
    private String getParamValue(Map<String, Object> params, String currentMessage, 
                                  String paramName, String paramDesc) {
        Object value = params.get(paramName);
        if (value != null && value instanceof String str && !str.isBlank()) {
            return str;
        }

        if (PARAM_TO.equals(paramName)) {
            return extractEmailFromText(currentMessage);
        }

        return null;
    }

    /**
     * 从文本中提取邮箱地址
     */
    private String extractEmailFromText(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }

        Pattern emailPattern = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
        Matcher matcher = emailPattern.matcher(text);
        if (matcher.find()) {
            return matcher.group();
        }
        return null;
    }

    /**
     * 验证邮箱格式
     */
    private boolean isValidEmail(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        Pattern pattern = Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");
        return pattern.matcher(email).matches();
    }

    @Override
    public com.aip.flow.dto.NodeSchema getInputSchema() {
        return com.aip.flow.dto.NodeSchema.builder()
                .type("object")
                .description("邮件发送参数")
                .properties(Map.of(
                    "to", com.aip.flow.dto.NodeSchema.Property.builder()
                            .type("string")
                            .description("收件人邮箱地址")
                            .required(true)
                            .build(),
                    "subject", com.aip.flow.dto.NodeSchema.Property.builder()
                            .type("string")
                            .description("邮件主题")
                            .required(true)
                            .build(),
                    "content", com.aip.flow.dto.NodeSchema.Property.builder()
                            .type("string")
                            .description("邮件内容")
                            .required(true)
                            .build()
                ))
                .build();
    }

    @Override
    public com.aip.flow.dto.NodeSchema getOutputSchema() {
        return com.aip.flow.dto.NodeSchema.builder()
                .type("object")
                .description("邮件发送结果")
                .properties(Map.of(
                    "email_sent", com.aip.flow.dto.NodeSchema.Property.builder()
                            .type("boolean")
                            .description("是否发送成功")
                            .build(),
                    "email_to", com.aip.flow.dto.NodeSchema.Property.builder()
                            .type("string")
                            .description("收件人邮箱")
                            .build(),
                    "email_subject", com.aip.flow.dto.NodeSchema.Property.builder()
                            .type("string")
                            .description("邮件主题")
                            .build()
                ))
                .build();
    }

    @Override
    public com.aip.flow.dto.NodeSchema getConfigSchema() {
        return com.aip.flow.dto.NodeSchema.builder()
                .type("object")
                .description("邮件节点配置")
                .properties(Map.of(
                    "subjectPrefix", com.aip.flow.dto.NodeSchema.Property.builder()
                            .type("string")
                            .description("邮件主题前缀，如【智能助手】")
                            .build(),
                    "requiredParams", com.aip.flow.dto.NodeSchema.Property.builder()
                            .type("array")
                            .description("自定义必需参数列表")
                            .build()
                ))
                .build();
    }
}

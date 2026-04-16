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
    private static final String PARAM_CC = "cc";           // 抄送地址（可选）
    private static final String PARAM_ATTACHMENTS = "attachments"; // 附件路径（可选）
    private static final String PARAM_IS_HTML = "isHtml";  // 是否HTML格式（可选）

    private static final String CONFIG_KEY_FROM = "from";         // 发件人（可选，默认配置的发件人）
    private static final String CONFIG_KEY_SUBJECT_PREFIX = "subjectPrefix"; // 主题前缀
    private static final String CONFIG_KEY_REQUIRED = "requiredParams"; // 必需参数列表
    private static final String CONFIG_KEY_SMART_ASSISTANT = "smartAssistant"; // 智能助手配置
    private static final String META_MISSING_PARAMS = "missing_params";
    private static final List<String> REQUIRED_EMAIL_PARAMS = List.of(PARAM_TO, PARAM_SUBJECT, PARAM_CONTENT);

    @PostConstruct
    public void init() {
        initBase("email_send", "发送邮件", 
                "发送邮件给指定用户，支持抄送、附件、HTML格式，当参数缺失时会智能询问用户提供", "execute",
                Arrays.asList("发送邮件", "发邮件", "邮箱", "邮件", "发到", "邮件发送", "给...发邮件"));
    }

    @Override
    public NodeResult execute(FlowContext context, Map<String, Object> config) {
        try {
            Map<String, Object> params = context.getParams();
            String currentMessage = context.getCurrentMessage();

            // 尝试从当前消息中提取邮件参数
            extractEmailParamsFromMessage(currentMessage, params);
            fillMissingParamsFromFollowUp(context, params, currentMessage);

            // 检查智能助手配置
            boolean useSmartAssistant = config != null && 
                Boolean.TRUE.equals(config.get(CONFIG_KEY_SMART_ASSISTANT));

            // 使用智能助手询问缺失的参数
            if (useSmartAssistant) {
                NodeResult smartResult = smartAskForMissingParams(context, params, currentMessage, config);
                if (smartResult != null) {
                    return smartResult;
                }
            } else {
                // 传统方式询问缺失参数
                NodeResult traditionalResult = traditionalAskForMissingParams(context, params, currentMessage);
                if (traditionalResult != null) {
                    return traditionalResult;
                }
            }

            // 获取所有参数
            String to = getParamValue(params, currentMessage, PARAM_TO, "邮箱");
            String subject = getParamValue(params, currentMessage, PARAM_SUBJECT, "邮件主题");
            String content = getParamValue(params, currentMessage, PARAM_CONTENT, "邮件内容");
            List<String> cc = getParamList(params, PARAM_CC);
            List<String> attachments = getParamList(params, PARAM_ATTACHMENTS);
            Boolean isHtml = getParamBoolean(params, PARAM_IS_HTML);

            // 验证必需参数
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

            // 应用主题前缀
            String subjectPrefix = (String) config.getOrDefault(CONFIG_KEY_SUBJECT_PREFIX, "");
            if (!subjectPrefix.isBlank()) {
                subject = subjectPrefix + subject;
            }

            // 发送邮件
            boolean success = sendMailWithParams(to, cc, subject, content, attachments, isHtml);

            if (success) {
                log.info("邮件发送成功: to={}, subject={}, cc={}, attachments={}", to, subject, cc, attachments);
                Map<String, Object> outputParams = new HashMap<>();
                outputParams.put("email_sent", true);
                outputParams.put("email_to", to);
                outputParams.put("email_subject", subject);
                outputParams.put("email_cc", cc);
                outputParams.put("email_attachments", attachments);
                outputParams.put("email_is_html", isHtml);
                return NodeResult.success(
                    String.format("邮件已成功发送到 %s，主题：%s%s", 
                        to, subject, cc != null && !cc.isEmpty() ? "（抄送：" + String.join(",", cc) + "）" : ""),
                    outputParams
                );
            } else {
                return NodeResult.fail("邮件发送失败，请稍后再试或联系管理员", "EMAIL_SEND_FAILED");
            }

        } catch (Exception e) {
            log.error("邮件发送异常: {}", e.getMessage(), e);
            return NodeResult.fail("邮件服务暂时不可用: " + e.getMessage(), "EMAIL_ERROR");
        }
    }

    /**
     * 使用智能助手询问缺失的参数
     */
    private NodeResult smartAskForMissingParams(FlowContext context, Map<String, Object> params, 
                                               String currentMessage, Map<String, Object> config) {
        try {
            List<String> missingParams = getMissingRequiredParams(params);
            if (missingParams.isEmpty()) {
                context.getMetadata().remove(META_MISSING_PARAMS);
                return null; // 没有缺失参数，继续执行
            }
            String nextMissingParam = missingParams.get(0);

            // 设置上下文状态，等待用户回复
            context.setStatus("waiting");
            context.getMetadata().put("last_node_type", "email_send");
            context.getMetadata().put(META_MISSING_PARAMS, missingParams);

            return NodeResult.needInput(buildPromptForParam(nextMissingParam));
            
        } catch (Exception e) {
            log.error("智能助手询问参数异常: {}", e.getMessage());
            return null; // 回退到传统方式
        }
    }
    
    /**
     * 传统方式询问缺失的参数
     */
    private NodeResult traditionalAskForMissingParams(FlowContext context, Map<String, Object> params, 
                                                     String currentMessage) {
        List<String> missingParams = getMissingRequiredParams(params);
        if (!missingParams.isEmpty()) {
            context.setStatus("waiting");
            context.getMetadata().put("last_node_type", "email_send");
            context.getMetadata().put(META_MISSING_PARAMS, missingParams);
            return NodeResult.needInput(buildPromptForParam(missingParams.get(0)));
        }

        context.getMetadata().remove(META_MISSING_PARAMS);
        
        return null; // 没有缺失参数，继续执行
    }
    
    /**
     * 根据参数发送邮件
     */
    private boolean sendMailWithParams(String to, List<String> cc, String subject, String content, 
                                     List<String> attachments, Boolean isHtml) {
        try {
            // 转换附件路径数组
            String[] attachmentArray = null;
            if (attachments != null && !attachments.isEmpty()) {
                attachmentArray = attachments.toArray(new String[0]);
            }
            
            // 根据参数选择发送方式
            if (cc != null && !cc.isEmpty()) {
                if (attachments != null && !attachments.isEmpty()) {
                    // 有抄送和附件
                    return mailService.sendAttachmentMail(List.of(to), cc, subject, content, attachmentArray);
                } else if (Boolean.TRUE.equals(isHtml)) {
                    // 有抄送和HTML格式
                    return mailService.sendCcHtmlMail(List.of(to), cc, subject, content);
                } else {
                    // 只有抄送
                    return mailService.sendCcMail(List.of(to), cc, subject, content);
                }
            } else {
                if (attachments != null && !attachments.isEmpty()) {
                    // 只有附件
                    return mailService.sendAttachmentMail(List.of(to), subject, content, attachmentArray);
                } else if (Boolean.TRUE.equals(isHtml)) {
                    // 只有HTML格式
                    return mailService.sendHtmlMail(to, subject, content);
                } else {
                    // 简单邮件
                    return mailService.sendSimpleMail(to, subject, content);
                }
            }
        } catch (Exception e) {
            log.error("发送邮件异常: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 填充缺失的参数（从后续对话中）
     */
    private void fillMissingParamsFromFollowUp(FlowContext context, Map<String, Object> params, String currentMessage) {
        if (currentMessage == null || currentMessage.trim().isEmpty()) {
            return;
        }

        boolean waitingForEmail = "waiting".equalsIgnoreCase(context.getStatus())
                || "email_send".equals(String.valueOf(context.getMetadata("last_node_type")));
        if (!waitingForEmail) {
            return;
        }

        String normalized = currentMessage.trim();
        if (normalized.isEmpty()) {
            return;
        }

        // 处理等待状态下的参数填充
        @SuppressWarnings("unchecked")
        List<String> missingParams = (List<String>) context.getMetadata(META_MISSING_PARAMS);
        if (missingParams != null) {
            String expectedParam = missingParams.stream()
                    .filter(param -> params.get(param) == null || (params.get(param) instanceof String s && s.trim().isEmpty()))
                    .findFirst()
                    .orElse(null);
            if (expectedParam != null) {
                if (PARAM_TO.equals(expectedParam)) {
                    String extractedEmail = extractEmailFromText(normalized);
                    if (extractedEmail != null) {
                        params.put(PARAM_TO, extractedEmail);
                    }
                }

                if (PARAM_SUBJECT.equals(expectedParam) && params.get(PARAM_SUBJECT) == null) {
                    params.put(PARAM_SUBJECT, normalized);
                }

                if (PARAM_CONTENT.equals(expectedParam) && params.get(PARAM_CONTENT) == null) {
                    params.put(PARAM_CONTENT, normalized);
                }

                if (PARAM_CC.equals(expectedParam) && params.get(PARAM_CC) == null) {
                    List<String> ccEmails = extractEmailsFromText(normalized);
                    if (!ccEmails.isEmpty()) {
                        params.put(PARAM_CC, ccEmails);
                    }
                }

                if (PARAM_ATTACHMENTS.equals(expectedParam) && params.get(PARAM_ATTACHMENTS) == null) {
                    List<String> attachmentPaths = extractAttachmentPaths(normalized);
                    if (!attachmentPaths.isEmpty()) {
                        params.put(PARAM_ATTACHMENTS, attachmentPaths);
                    }
                }

                context.getMetadata().put(META_MISSING_PARAMS, getMissingRequiredParams(params));
                return;
            }
        }
        
        // 兼容原有的填充逻辑
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
        if (message == null || message.trim().isEmpty()) {
            return;
        }

        // 提取收件人邮箱
        if (params.get(PARAM_TO) == null) {
            String extractedEmail = extractEmailFromText(message);
            if (extractedEmail != null) {
                params.put(PARAM_TO, extractedEmail);
            }
        }

        // 提抄送地址
        if (params.get(PARAM_CC) == null) {
            List<String> ccEmails = extractEmailsFromText(message);
            if (!ccEmails.isEmpty()) {
                params.put(PARAM_CC, ccEmails);
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
        
        // 提取附件
        if (params.get(PARAM_ATTACHMENTS) == null) {
            List<String> attachments = extractAttachmentPaths(message);
            if (!attachments.isEmpty()) {
                params.put(PARAM_ATTACHMENTS, attachments);
            }
        }
        
        // 检查是否HTML格式
        if (params.get(PARAM_IS_HTML) == null) {
            Boolean isHtml = checkIfHtmlFormat(message);
            if (isHtml != null) {
                params.put(PARAM_IS_HTML, isHtml);
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
            if (value == null || (value instanceof String && ((String) value).trim().isEmpty())) {
                missing.add(param);
            }
        }
        return missing;
    }

    private List<String> getMissingRequiredParams(Map<String, Object> params) {
        return checkMissingParams(params, REQUIRED_EMAIL_PARAMS);
    }

    private String buildPromptForParam(String param) {
        return switch (param) {
            case PARAM_TO -> "请先告诉我收件人邮箱地址。";
            case PARAM_SUBJECT -> "好的，请告诉我邮件主题。";
            case PARAM_CONTENT -> "收到，请告诉我邮件正文内容。";
            case PARAM_CC -> "如需抄送，请提供抄送邮箱（多个可用逗号分隔）；不需要可回复“无”。";
            case PARAM_ATTACHMENTS -> "如需附件，请提供附件路径（多个可用逗号分隔）；不需要可回复“无”。";
            default -> "请补充必要的邮件信息。";
        };
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
        if (value != null && value instanceof String && !((String) value).trim().isEmpty()) {
            return (String) value;
        }

        if (PARAM_TO.equals(paramName)) {
            return extractEmailFromText(currentMessage);
        }

        return null;
    }

    /**
     * 从文本中提取多个邮箱地址（用于抄送）
     */
    private List<String> extractEmailsFromText(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new ArrayList<>();
        }

        List<String> emails = new ArrayList<>();
        Pattern emailPattern = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
        Matcher matcher = emailPattern.matcher(text);
        
        while (matcher.find()) {
            emails.add(matcher.group());
        }
        
        return emails;
    }

    /**
     * 从文本中提取附件路径
     */
    private List<String> extractAttachmentPaths(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new ArrayList<>();
        }

        List<String> attachments = new ArrayList<>();
        
        // 匹配文件路径模式
        String[] patterns = {
            "附件[：:]\\s*([^,，。]+?)(?:[,，。]|$)",
            "文件[：:]\\s*([^,，。]+?)(?:[,，。]|$)",
            "attachment[:\\s]\\s*([^,，。]+?)(?:[,，。]|$)",
            "/[^\\s]+\\.(pdf|doc|docx|txt|jpg|png|gif)"
        };

        for (String pattern : patterns) {
            Matcher m = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(text);
            while (m.find()) {
                String path = m.group(1);
                if (path != null && !path.trim().isEmpty()) {
                    attachments.add(path.trim());
                }
            }
        }
        
        return attachments;
    }

    /**
     * 检查是否为HTML格式
     */
    private Boolean checkIfHtmlFormat(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        
        String lowerText = text.toLowerCase();
        if (lowerText.contains("<html") || lowerText.contains("<div") || 
            lowerText.contains("<p>") || lowerText.contains("<br>")) {
            return true;
        }
        
        if (lowerText.contains("html格式") || lowerText.contains("富文本")) {
            return true;
        }
        
        return null;
    }

    /**
     * 获取字符串列表参数
     */
    @SuppressWarnings("unchecked")
    private List<String> getParamList(Map<String, Object> params, String paramName) {
        Object value = params.get(paramName);
        if (value instanceof List) {
            return (List<String>) value;
        }
        if (value instanceof String) {
            String str = (String) value;
            if (str.trim().isEmpty()) {
                return new ArrayList<>();
            }
            // 如果是逗号分隔的字符串，分割成列表
            return Arrays.asList(str.split("[,，]"));
        }
        return new ArrayList<>();
    }

    /**
     * 获取布尔参数
     */
    private Boolean getParamBoolean(Map<String, Object> params, String paramName) {
        Object value = params.get(paramName);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return null;
    }
    private String extractEmailFromText(String text) {
        if (text == null || text.trim().isEmpty()) {
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
        if (email == null || email.trim().isEmpty()) {
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
                            .description("收件人邮箱地址（必需）")
                            .required(true)
                            .build(),
                    "subject", com.aip.flow.dto.NodeSchema.Property.builder()
                            .type("string")
                            .description("邮件主题（必需）")
                            .required(true)
                            .build(),
                    "content", com.aip.flow.dto.NodeSchema.Property.builder()
                            .type("string")
                            .description("邮件内容（必需）")
                            .required(true)
                            .build(),
                    "cc", com.aip.flow.dto.NodeSchema.Property.builder()
                            .type("array")
                            .description("抄送地址列表（可选）")
                            .items(com.aip.flow.dto.NodeSchema.Property.builder()
                                    .type("string")
                                    .build())
                            .build(),
                    "attachments", com.aip.flow.dto.NodeSchema.Property.builder()
                            .type("array")
                            .description("附件文件路径列表（可选）")
                            .items(com.aip.flow.dto.NodeSchema.Property.builder()
                                    .type("string")
                                    .build())
                            .build(),
                    "isHtml", com.aip.flow.dto.NodeSchema.Property.builder()
                            .type("boolean")
                            .description("是否为HTML格式邮件（可选）")
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
                            .build(),
                    "email_cc", com.aip.flow.dto.NodeSchema.Property.builder()
                            .type("array")
                            .description("抄送地址列表")
                            .items(com.aip.flow.dto.NodeSchema.Property.builder()
                                    .type("string")
                                    .build())
                            .build(),
                    "email_attachments", com.aip.flow.dto.NodeSchema.Property.builder()
                            .type("array")
                            .description("附件列表")
                            .items(com.aip.flow.dto.NodeSchema.Property.builder()
                                    .type("string")
                                    .build())
                            .build(),
                    "email_is_html", com.aip.flow.dto.NodeSchema.Property.builder()
                            .type("boolean")
                            .description("是否为HTML格式")
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
                            .items(com.aip.flow.dto.NodeSchema.Property.builder()
                                    .type("string")
                                    .build())
                            .build(),
                    "smartAssistant", com.aip.flow.dto.NodeSchema.Property.builder()
                            .type("boolean")
                            .description("是否启用智能助手询问模式")
                            .defaultValue(true)
                            .build()
                ))
                .build();
    }
}

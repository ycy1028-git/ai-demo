package com.aip.flow.service.impl;

import com.aip.ai.entity.AiModelConfig;
import com.aip.ai.service.IAiModelConfigService;
import com.aip.common.ai.UnifiedLlmService;
import com.aip.common.ai.UnifiedLlmService.Message;
import com.aip.flow.dto.IntentRouteResult;
import com.aip.flow.entity.FlowTemplate;
import com.aip.flow.mapper.FlowTemplateMapper;
import com.aip.flow.service.IIntentRouterService;
import com.aip.flow.service.INodeRegistryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 意图路由服务实现
 * <p>
 * 核心职责：
 * 1. 快速匹配（关键词）- 无需调用 LLM，直接匹配模板关键词
 * 2. LLM 意图分析 - 当快速匹配失败时，调用 LLM 分析用户意图
 * 3. 返回路由结果 - 包括路由类型、匹配的模板、抽取的参数、置信度等
 * <p>
 * 路由类型：
 * - FIXED_TEMPLATE: 匹配固定模板（复杂业务场景，如报销、请假）
 * - DYNAMIC_PLAN: 动态规划（简单场景，LLM 自动选择节点）
 * - DIRECT_ANSWER: 直接回答（简单问答）
 * - FALLBACK: 兜底处理（无法匹配时的默认处理）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntentRouterService implements IIntentRouterService {

    /** 流程模板 Mapper，用于查询模板 */
    private final FlowTemplateMapper flowTemplateMapper;

    /** 节点注册服务，用于获取可用节点描述 */
    private final INodeRegistryService nodeRegistryService;

    /** AI 模型配置服务，用于获取默认模型 */
    private final IAiModelConfigService aiModelConfigService;

    /** 统一 LLM 调用服务 */
    private final UnifiedLlmService unifiedLlmService;

    /** JSON 解析器 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final List<String> KNOWLEDGE_HINT_WORDS = List.of(
            "制度", "规定", "政策", "手册", "文档", "流程", "说明", "怎么", "如何", "为什么", "是什么",
            "能否", "是否", "区别", "作用", "原理", "配置", "报错", "接口", "参数", "上限", "权限"
    );

    private static final List<String> FLOW_ACTION_WORDS = List.of(
            "提交", "创建", "发起", "审批", "报销", "请假", "工单", "新增", "删除", "修改", "更新", "办理"
    );

    // 邮件发送关键词
    private static final List<String> EMAIL_KEYWORDS = List.of(
            "发邮件", "发送邮件", "寄邮件", "邮件", "邮箱地址", "@"
    );

    /**
     * 执行意图路由（入口方法）
     * <p>
     * 路由流程：
     * 1. 快速匹配 - 基于模板的 matchPattern 关键词匹配（快速、无成本）
     * 2. 如果快速匹配失败，调用 LLM 进行意图分析
     * 3. LLM 返回结构化结果（路由类型、模板编码、参数、置信度等）
     *
     * @param userMessage 用户消息
     * @param context 流程上下文（用于多轮对话时的上下文感知）
     * @return 意图路由结果
     */
    @Override
    public IntentRouteResult route(String userMessage, com.aip.flow.engine.FlowContext context) {
        try {
            // ========== 1. LLM 意图分析（优先） ==========
            // 获取所有启用的模板
            List<FlowTemplate> templates = flowTemplateMapper.findAllEnabledOrderByPriority();
            if (templates.isEmpty()) {
                log.warn("未配置任何模板，使用兜底方案");
                return buildFallbackResult();
            }

            // 获取默认 AI 模型
            AiModelConfig modelConfig = aiModelConfigService.getDefaultModel();
            
            // 构建路由提示词（包含模板列表、节点描述、输出格式要求）
            String prompt = buildRoutingPrompt(userMessage, templates, context);
            List<Message> messages = List.of(new Message("user", prompt));

            log.info("意图路由开始: userMessage={}", userMessage);
            
            // 调用 LLM 获取路由决策
            String response = unifiedLlmService.chat(modelConfig, messages);

            // 解析 LLM 返回结果
            return parseRoutingResult(response, templates, userMessage);

        } catch (Exception e) {
            log.error("意图路由异常: error={}", e.getMessage(), e);
            // 异常时降级到快速匹配，再兜底
            String quickMatch = quickMatchTemplate(userMessage);
            if (quickMatch != null) {
                return buildFixedTemplateResult(quickMatch);
            }
            return buildFallbackResult();
        }
    }

    /**
     * 邮件快速路由检查
     * 检测用户消息是否包含邮件发送意图，并尝试提取参数
     */
    private IntentRouteResult checkEmailRoute(String userMessage, com.aip.flow.engine.FlowContext context) {
        // 检查是否包含邮件关键词
        boolean hasEmailIntent = EMAIL_KEYWORDS.stream()
                .anyMatch(keyword -> userMessage.toLowerCase().contains(keyword.toLowerCase()));
        
        // 检查是否包含邮箱地址
        boolean hasEmailAddress = userMessage.matches(".*[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}.*");
        
        if (!hasEmailIntent && !hasEmailAddress) {
            return null;
        }
        
        log.info("检测到邮件意图: hasEmailIntent={}, hasEmailAddress={}", hasEmailIntent, hasEmailAddress);
        
        // 提取邮箱地址
        String email = extractEmailFromText(userMessage);
        String subject = extractSubjectFromText(userMessage);
        String content = extractContentFromText(userMessage);
        
        // 构建结果：返回动态规划，但标记需要使用 email_send 节点
        Map<String, Object> params = new HashMap<>();
        if (email != null) {
            params.put("to", email);
        }
        if (subject != null) {
            params.put("subject", subject);
        }
        if (content != null) {
            params.put("content", content);
        }
        
        // 判断是否需要继续收集参数
        boolean needMoreInput = email == null || subject == null || content == null;
        String prompt = null;
        if (needMoreInput) {
            if (email == null) {
                prompt = "请问您希望将邮件发送到哪个邮箱地址？";
            } else if (subject == null) {
                prompt = "请告诉我邮件的主题是什么？";
            } else if (content == null) {
                prompt = "请告诉我邮件的内容是什么？";
            }
        }
        
        return IntentRouteResult.builder()
                .routeType(IntentRouteResult.RouteType.DYNAMIC_PLAN)
                .intent("发送邮件")
                .params(params)
                .confidence(0.9)
                .needMoreInput(needMoreInput)
                .prompt(prompt)
                .routeReason("检测到邮件发送意图")
                .build();
    }

    /**
     * 从文本中提取邮箱地址
     */
    private String extractEmailFromText(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("([a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})");
        java.util.regex.Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * 从文本中提取邮件主题
     */
    private String extractSubjectFromText(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String[] patterns = {
            "主题[是为：:]\\s*(.+?)(?:，|,|。|$)",
            "标题[是为：:]\\s*(.+?)(?:，|,|。|$)"
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
        if (text == null || text.isBlank()) {
            return null;
        }
        String[] patterns = {
            "内容[是为：:]\\s*(.+?)(?:，|,|。|$)",
            "说[是为：:]\\s*(.+?)(?:，|,|。|$)"
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
     * 快速匹配模板（基于关键词）
     * <p>
     * 使用模板的 matchPattern 字段进行正则匹配
     * 例如：matchPattern = "报销.*|差旅.*" 会匹配 "我想报销差旅费"
     *
     * @param userMessage 用户消息
     * @return 匹配的模板编码，未匹配返回 null
     */
    @Override
    public String quickMatchTemplate(String userMessage) {
        List<FlowTemplate> templates = flowTemplateMapper.findAllEnabledOrderByPriority();

        for (FlowTemplate template : templates) {
            String pattern = template.getMatchPattern();
            if (pattern != null && !pattern.isBlank()) {
                try {
                    // 正则匹配：.*pattern.* 允许关键词出现在任意位置
                    if (Pattern.matches(".*" + pattern + ".*", userMessage)) {
                        return template.getTemplateCode();
                    }
                } catch (Exception e) {
                    log.warn("匹配模式解析失败: template={}, pattern={}", 
                            template.getTemplateCode(), pattern);
                }
            }
        }

        return null;
    }

    /**
     * 构建固定模板路由结果
     * <p>
     * 快速匹配成功时调用，直接返回固定模板路由
     */
    private IntentRouteResult buildFixedTemplateResult(String templateCode) {
        return flowTemplateMapper.findByTemplateCodeAndStatusAndDeletedFalse(templateCode, 1)
                .map(template -> IntentRouteResult.builder()
                        .routeType(IntentRouteResult.RouteType.FIXED_TEMPLATE)
                        .templateCode(template.getTemplateCode())
                        .templateName(template.getTemplateName())
                        .confidence(1.0)  // 快速匹配置信度为 1.0
                        .needMoreInput(false)
                        .requiresKnowledgeRetrieval(false)
                        .routeReason("命中固定模板，优先走业务流程")
                        .build())
                .orElse(buildFallbackResult());
    }

    /**
     * 构建兜底路由结果
     * <p>
     * 当无法匹配任何模板时使用兜底方案：
     * 1. 优先查找 is_fallback=1 的兜底模板
     * 2. 没有兜底模板则降级到动态规划
     */
    private IntentRouteResult buildFallbackResult() {
        return flowTemplateMapper.findFallbackTemplate()
                .map(template -> IntentRouteResult.builder()
                        .routeType(IntentRouteResult.RouteType.FALLBACK)
                        .templateCode(template.getTemplateCode())
                        .templateName(template.getTemplateName())
                        .confidence(0.5)
                        .needMoreInput(false)
                        .requiresKnowledgeRetrieval(true)
                        .routeReason("兜底模板，默认尝试知识检索增强回答")
                        .build())
                .orElse(IntentRouteResult.builder()
                        .routeType(IntentRouteResult.RouteType.DYNAMIC_PLAN)
                        .confidence(0.3)
                        .needMoreInput(false)
                        .requiresKnowledgeRetrieval(true)
                        .routeReason("未命中模板，动态规划场景默认尝试知识检索")
                        .build());
    }

    /**
     * 构建路由提示词
     * <p>
     * 提示词包含：
     * 1. 对话上下文（当前模板、已收集参数、状态）- 用于多轮对话
     * 2. 可用模板列表（按优先级排序）- LLM 据此选择模板
     * 3. 可用节点说明 - 用于动态规划时选择节点
     * 4. 输出格式要求 - JSON 格式，包含 routeType、templateCode、params 等
     */
    private String buildRoutingPrompt(String userMessage, List<FlowTemplate> templates,
                                      com.aip.flow.engine.FlowContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 意图分析任务\n\n");
        sb.append("分析用户消息，判断最合适的处理方式。\n\n");

        // ========== 对话上下文（多轮对话时） ==========
        if (context != null && context.getTemplateCode() != null) {
            sb.append("【当前对话上下文】\n");
            sb.append("- 当前模板：").append(context.getTemplateCode()).append("\n");
            sb.append("- 已收集参数：").append(context.getParams()).append("\n");
            sb.append("- 当前状态：").append(context.getStatus()).append("\n\n");
        }

        // ========== 可用模板列表 ==========
        sb.append("【可用流程模板】（按优先级排序）\n");
        for (FlowTemplate template : templates) {
            // 标记兜底和支持动态的模板
            String fallback = template.getIsFallback() == 1 ? " [兜底]" : "";
            String dynamic = template.getIsDynamic() == 1 ? " [支持动态]" : "";
            sb.append(String.format("- %s: %s (%s)%s%s\n",
                    template.getTemplateCode(),
                    template.getTemplateName(),
                    template.getDescription(),
                    fallback,
                    dynamic));

            // 添加匹配说明，帮助 LLM 理解何时使用该模板
            if (template.getMatchPrompt() != null && !template.getMatchPrompt().isBlank()) {
                sb.append(String.format("  匹配说明: %s\n", template.getMatchPrompt()));
            }
        }
        sb.append("\n");

        // ========== 可用节点（供动态规划使用） ==========
        sb.append(nodeRegistryService.generateNodesSection());
        sb.append("\n");

        // ========== 输出格式要求 ==========
        sb.append("【输出格式要求】\n");
        sb.append("请以 JSON 格式返回，示例：\n");
        sb.append("""
            {
              "routeType": "FIXED_TEMPLATE",
              "templateCode": "reimbursement",
              "intent": "费用报销",
              "confidence": 0.95,
              "params": {"amount": 1000},
              "nextAction": null,
              "requiresKnowledgeRetrieval": false,
              "prompt": null
            }

            routeType 可选值：
            - FIXED_TEMPLATE: 匹配固定模板
            - DYNAMIC_PLAN: 需要动态规划
            - DIRECT_ANSWER: 直接回答（简单问答）
            - FALLBACK: 兜底处理

            注意事项：
            - 优先匹配固定模板（高优先级）
            - 复杂业务场景使用固定模板
            - 若识别为“内部知识查询/制度问答”，设置 requiresKnowledgeRetrieval=true，routeType=DIRECT_ANSWER
            - 若识别为“内部节点能力调用”（如 email_send 等），设置 routeType=DYNAMIC_PLAN 且 nextAction 为节点类型
            - confidence 低于 0.7 时返回 prompt 追问用户
            """);

        // ========== 用户问题 ==========
        sb.append("\n【用户问题】\n");
        sb.append(userMessage);

        return sb.toString();
    }

    /**
     * 解析 LLM 返回的路由结果
     * <p>
     * 处理逻辑：
     * 1. 提取 JSON 字符串
     * 2. 解析各字段（routeType、templateCode、params、confidence 等）
     * 3. 验证模板编码合法性
     * 4. 根据置信度决定是否需要追问
     */
    private IntentRouteResult parseRoutingResult(String response, List<FlowTemplate> templates, String userMessage) {
        try {
            // 提取 JSON 字符串（可能包含其他文本）
            String jsonStr = extractJson(response);
            if (jsonStr == null) {
                log.warn("无法解析路由结果 JSON，使用兜底方案");
                return buildFallbackResult();
            }

            JsonNode jsonNode = objectMapper.readTree(jsonStr);
            
            // 解析路由类型
            String routeTypeStr = jsonNode.has("routeType") 
                    ? jsonNode.get("routeType").asText() : "DYNAMIC_PLAN";
            IntentRouteResult.RouteType routeType = parseRouteType(routeTypeStr);

            // ========== 提取并验证模板编码 ==========
            String templateCode = null;
            if (jsonNode.has("templateCode") && !jsonNode.get("templateCode").isNull()) {
                templateCode = jsonNode.get("templateCode").asText();
            }

            // 固定模板路由必须验证模板编码合法性
            if (routeType == IntentRouteResult.RouteType.FIXED_TEMPLATE) {
                if (templateCode == null || !isValidTemplateCode(templateCode, templates)) {
                    log.warn("LLM 返回的模板编码无效: {}, 使用兜底方案", templateCode);
                    return buildFallbackResult();
                }
            }

            // ========== 提取置信度 ==========
            double confidence = jsonNode.has("confidence") 
                    ? jsonNode.get("confidence").asDouble() : 0.5;

            // ========== 提取追问提示 ==========
            String prompt = null;
            if (jsonNode.has("prompt") && !jsonNode.get("prompt").isNull()) {
                prompt = jsonNode.get("prompt").asText();
            }

            // ========== 提取业务参数（LLM 抽取） ==========
            Map<String, Object> params = new HashMap<>();
            if (jsonNode.has("params") && jsonNode.get("params").isObject()) {
                jsonNode.get("params").fields().forEachRemaining(entry ->
                        params.put(entry.getKey(), convertJsonNode(entry.getValue()))
                );
            }

            // ========== 获取模板名称 ==========
            String templateName = null;
            final String finalTemplateCode = templateCode;
            if (finalTemplateCode != null) {
                templateName = templates.stream()
                        .filter(t -> t.getTemplateCode().equals(finalTemplateCode))
                        .findFirst()
                        .map(FlowTemplate::getTemplateName)
                        .orElse(null);
            }

            // ========== 构建路由结果 ==========
            IntentRouteResult result = IntentRouteResult.builder()
                    .routeType(routeType)
                    .templateCode(templateCode)
                    .templateName(templateName)
                    .intent(jsonNode.has("intent") ? jsonNode.get("intent").asText() : null)
                    .params(params)
                    .confidence(confidence)
                    .prompt(prompt)
                    .nextAction(jsonNode.has("nextAction") && !jsonNode.get("nextAction").isNull()
                            ? jsonNode.get("nextAction").asText()
                            : null)
                    // 仅当模型明确给出追问内容时，才进入等待输入
                    .needMoreInput(prompt != null && !prompt.isBlank())
                    .requiresKnowledgeRetrieval(jsonNode.has("requiresKnowledgeRetrieval")
                            ? jsonNode.get("requiresKnowledgeRetrieval").asBoolean()
                            : shouldRetrieveKnowledge(routeType,
                                    jsonNode.has("intent") ? jsonNode.get("intent").asText() : null,
                                    jsonNode.has("routeReason") && !jsonNode.get("routeReason").isNull()
                                            ? jsonNode.get("routeReason").asText()
                                            : userMessage))
                    .routeReason(jsonNode.has("routeReason") && !jsonNode.get("routeReason").isNull()
                            ? jsonNode.get("routeReason").asText()
                            : "基于语义规则自动判定")
                    .build();

            log.info("路由结果: routeType={}, templateCode={}, confidence={}",
                    routeType, templateCode, confidence);

            return applySemanticGuard(result, userMessage);

        } catch (Exception e) {
            log.error("解析路由结果失败: {}", e.getMessage());
            return buildFallbackResult();
        }
    }

    private boolean shouldRetrieveKnowledge(IntentRouteResult.RouteType routeType, String intent, String text) {
        if (routeType == IntentRouteResult.RouteType.FIXED_TEMPLATE) {
            return false;
        }

        String merged = ((intent == null ? "" : intent) + " " + (text == null ? "" : text)).toLowerCase();

        for (String actionWord : FLOW_ACTION_WORDS) {
            if (merged.contains(actionWord.toLowerCase())) {
                return false;
            }
        }

        for (String hintWord : KNOWLEDGE_HINT_WORDS) {
            if (merged.contains(hintWord.toLowerCase())) {
                return true;
            }
        }

        return routeType == IntentRouteResult.RouteType.DIRECT_ANSWER
                || routeType == IntentRouteResult.RouteType.FALLBACK;
    }

    /**
     * 解析路由类型枚举
     */
    private IntentRouteResult.RouteType parseRouteType(String routeTypeStr) {
        try {
            return IntentRouteResult.RouteType.valueOf(routeTypeStr.toUpperCase());
        } catch (Exception e) {
            return IntentRouteResult.RouteType.DYNAMIC_PLAN;
        }
    }

    /**
     * 验证模板编码是否合法
     */
    private boolean isValidTemplateCode(String templateCode, List<FlowTemplate> templates) {
        return templates.stream()
                .anyMatch(t -> t.getTemplateCode().equals(templateCode));
    }

    /**
     * 从 LLM 响应中提取 JSON 字符串
     * <p>
     * LLM 返回可能包含一些解释性文本，需要提取其中的 JSON 部分
     */
    private String extractJson(String response) {
        int start = response.indexOf("{");
        int end = response.lastIndexOf("}");
        if (start >= 0 && end > start) {
            return response.substring(start, end + 1);
        }
        return null;
    }

    /**
     * 转换 JSON 节点为 Java 对象
     */
    private Object convertJsonNode(JsonNode node) {
        if (node.isTextual()) return node.asText();
        if (node.isNumber()) return node.asDouble();
        if (node.isBoolean()) return node.asBoolean();
        if (node.isNull()) return null;
        return node.toString();
    }

    private IntentRouteResult applySemanticGuard(IntentRouteResult result, String userMessage) {
        if (result == null) {
            return null;
        }

        boolean emailIntent = isEmailIntent(userMessage, result.getIntent(), result.getRouteReason(), result.getNextAction());
        if (!emailIntent) {
            return result;
        }

        if (result.getRouteType() == IntentRouteResult.RouteType.DIRECT_ANSWER
                || result.getRouteType() == IntentRouteResult.RouteType.FALLBACK) {
            result.setRouteType(IntentRouteResult.RouteType.DYNAMIC_PLAN);
        }

        if (result.getNextAction() == null || result.getNextAction().isBlank()) {
            result.setNextAction("email_send");
        }

        if (result.getParams() == null) {
            result.setParams(new HashMap<>());
        }

        if (!result.getParams().containsKey("to")) {
            String email = extractEmailFromText(userMessage);
            if (email != null) {
                result.getParams().put("to", email);
            }
        }
        if (!result.getParams().containsKey("subject")) {
            String subject = extractSubjectFromText(userMessage);
            if (subject != null) {
                result.getParams().put("subject", subject);
            }
        }
        if (!result.getParams().containsKey("content")) {
            String content = extractContentFromText(userMessage);
            if (content != null) {
                result.getParams().put("content", content);
            }
        }

        // 由节点负责逐步追问参数，避免路由层进入无提示 waiting
        result.setNeedMoreInput(false);
        result.setPrompt(null);
        result.setRequiresKnowledgeRetrieval(false);

        String reason = result.getRouteReason();
        if (reason == null || reason.isBlank()) {
            result.setRouteReason("语义护栏: 邮件操作");
        }

        return result;
    }

    private boolean isEmailIntent(String userMessage, String intent, String reason, String nextAction) {
        if (nextAction != null && nextAction.toLowerCase().contains("email")) {
            return true;
        }

        String merged = ((userMessage == null ? "" : userMessage) + " "
                + (intent == null ? "" : intent) + " "
                + (reason == null ? "" : reason)).toLowerCase();

        for (String keyword : EMAIL_KEYWORDS) {
            if (merged.contains(keyword.toLowerCase())) {
                return true;
            }
        }

        return merged.contains("@");
    }
}

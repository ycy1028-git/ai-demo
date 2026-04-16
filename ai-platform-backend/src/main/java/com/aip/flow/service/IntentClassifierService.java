package com.aip.flow.service;

import com.aip.ai.entity.AiModelConfig;
import com.aip.ai.service.IAiModelConfigService;
import com.aip.flow.engine.FlowContext;
import com.aip.flow.dto.IntentType;
import com.aip.common.ai.UnifiedLlmService;
import com.aip.common.ai.UnifiedLlmService.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * 意图分类器服务
 * 基于多种策略进行意图识别：关键词、传统规则、LLM语义分析
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntentClassifierService {

    private final UnifiedLlmService llmService;
    private final IAiModelConfigService aiModelConfigService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 意图分类的Prompt模板
     */
    private static final String INTENT_CLASSIFICATION_PROMPT = """
        你是一个智能助手意图分类器，请分析用户消息并返回意图类型。
        
        用户消息：{userMessage}
        
        上下文信息：
        - 历史对话：{contextHistory}
        - 当前状态：{currentState}
        
        意图类型定义：
        1. KNOWLEDGE_QUERY - 查询知识、政策、制度、手册等静态信息
        2. ACTION_EXECUTION - 执行操作、提交申请、创建流程等动态操作
        3. CONVERSATION - 闲聊、问候、日常对话等
        4. AMBIGUOUS - 意图不明确或混合意图
        
        请返回JSON格式：
        {{
          "intent": "KNOWLEDGE_QUERY|ACTION_EXECUTION|CONVERSATION|AMBIGUOUS",
          "confidence": 0.95,
          "reason": "分类理由说明"
        }}
        
        注意：
        - 如果消息同时包含知识查询和操作执行，判断主要意图
        - 置信度低于0.7时返回AMBIGUOUS
        - 分类理由要简明扼要
        """;

    /**
     * 分类用户意图
     * @param userMessage 用户消息
     * @param context 流程上下文
     * @return 意图分类结果
     */
    public IntentType classify(String userMessage, FlowContext context) {
        try {
            // 第一层：快速关键词匹配（高性能）
            IntentType quickResult = quickClassify(userMessage);
            if (quickResult != null && quickResult != IntentType.AMBIGUOUS) {
                log.debug("快速分类成功: userMessage={}, intent={}", userMessage, quickResult);
                return quickResult;
            }

            // 第二层：LLM语义分析（高准确性）
            IntentType llmResult = llmClassify(userMessage, context);
            if (llmResult != IntentType.AMBIGUOUS) {
                log.debug("LLM分类成功: userMessage={}, intent={}", userMessage, llmResult);
                return llmResult;
            }

            // 第三层：规则兜底
            IntentType ruleResult = ruleBasedClassify(userMessage);
            log.debug("规则分类结果: userMessage={}, intent={}", userMessage, ruleResult);
            
            return ruleResult;

        } catch (Exception e) {
            log.error("意图分类异常: userMessage={}, error={}", userMessage, e.getMessage());
            return IntentType.AMBIGUOUS;
        }
    }

    /**
     * 快速关键词分类
     */
    private IntentType quickClassify(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return IntentType.CONVERSATION;
        }

        String normalized = userMessage.toLowerCase().trim();

        // 问候语检测
        if (isGreeting(normalized)) {
            return IntentType.CONVERSATION;
        }

        // 简单知识查询检测
        if (isSimpleKnowledgeQuery(normalized)) {
            return IntentType.KNOWLEDGE_QUERY;
        }

        // 简单操作请求检测
        if (isSimpleActionRequest(normalized)) {
            return IntentType.ACTION_EXECUTION;
        }

        return IntentType.AMBIGUOUS;
    }

    /**
     * LLM语义分类
     */
    private IntentType llmClassify(String userMessage, FlowContext context) {
        try {
            // 构建分类提示词
            String prompt = buildClassificationPrompt(userMessage, context);
            List<Message> messages = List.of(new Message("user", prompt));
            AiModelConfig modelConfig = aiModelConfigService.getDefaultModel();

            // 调用LLM进行分类
            String response = llmService.chat(modelConfig, messages);

            // 解析分类结果
            return parseClassificationResponse(response);

        } catch (Exception e) {
            log.warn("LLM分类失败，降级到规则分类: userMessage={}, error={}", 
                    userMessage, e.getMessage());
            return IntentType.AMBIGUOUS;
        }
    }

    /**
     * 基于规则的分类（兜底方案）
     */
    private IntentType ruleBasedClassify(String userMessage) {
        String normalized = userMessage.toLowerCase().trim();

        // 检查是否包含知识查询特征
        if (containsKnowledgeFeatures(normalized)) {
            return IntentType.KNOWLEDGE_QUERY;
        }

        // 检查是否包含操作执行特征
        if (containsActionFeatures(normalized)) {
            return IntentType.ACTION_EXECUTION;
        }

        // 默认返回对话类
        return IntentType.CONVERSATION;
    }

    /**
     * 构建分类提示词
     */
    private String buildClassificationPrompt(String userMessage, FlowContext context) {
        String contextHistory = context != null && context.getHistory() != null 
            ? String.join(" | ", context.getHistory())
            : "无历史记录";

        String currentState = context != null && context.getMetadata() != null
            ? context.getMetadata().toString()
            : "无特殊状态";

        return INTENT_CLASSIFICATION_PROMPT
            .replace("{userMessage}", userMessage)
            .replace("{contextHistory}", contextHistory)
            .replace("{currentState}", currentState);
    }

    /**
     * 解析LLM分类响应
     */
    private IntentType parseClassificationResponse(String response) {
        try {
            // 提取JSON部分
            String jsonStr = extractJson(response);
            if (jsonStr == null) {
                return IntentType.AMBIGUOUS;
            }

            JsonNode jsonNode = objectMapper.readTree(jsonStr);
            
            // 解析意图类型
            String intentStr = jsonNode.has("intent") 
                ? jsonNode.get("intent").asText() 
                : "AMBIGUOUS";
            
            // 解析置信度
            double confidence = jsonNode.has("confidence")
                ? jsonNode.get("confidence").asDouble()
                : 0.0;

            // 置信度过低则返回模糊
            if (confidence < 0.7) {
                return IntentType.AMBIGUOUS;
            }

            return IntentType.valueOf(intentStr);

        } catch (Exception e) {
            log.warn("解析分类响应失败: response={}, error={}", response, e.getMessage());
            return IntentType.AMBIGUOUS;
        }
    }

    /**
     * 从响应中提取JSON
     */
    private String extractJson(String response) {
        int start = response.indexOf("{");
        int end = response.lastIndexOf("}");
        if (start >= 0 && end > start) {
            return response.substring(start, end + 1);
        }
        return null;
    }

    // ========== 辅助判断方法 ==========

    private boolean isGreeting(String normalized) {
        String[] greetings = {"你好", "您好", "嗨", "hello", "hi", "在吗", "在不在"};
        for (String greeting : greetings) {
            if (normalized.contains(greeting)) {
                return true;
            }
        }
        return false;
    }

    private boolean isSimpleKnowledgeQuery(String normalized) {
        return normalized.contains("?") && 
               !containsActionVerbs(normalized) &&
               normalized.length() < 100;
    }

    private boolean isSimpleActionRequest(String normalized) {
        return containsActionVerbs(normalized) && 
               !normalized.contains("?") &&
               normalized.length() < 80;
    }

    private boolean containsKnowledgeFeatures(String normalized) {
        String[] knowledgeKeywords = {
            "制度", "规定", "政策", "手册", "文档", "流程", "说明", 
            "怎么", "如何", "为什么", "是什么", "能否", "是否", 
            "区别", "作用", "原理", "配置", "报错", "接口", "参数"
        };
        
        for (String keyword : knowledgeKeywords) {
            if (normalized.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsActionFeatures(String normalized) {
        String[] actionKeywords = {
            "提交", "创建", "发起", "审批", "报销", "请假", "工单", 
            "新增", "删除", "修改", "更新", "办理", "申请", "执行"
        };
        
        for (String keyword : actionKeywords) {
            if (normalized.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsActionVerbs(String normalized) {
        String[] actionVerbs = {
            "提交", "创建", "发起", "审批", "报销", "请假", "新增", 
            "删除", "修改", "更新", "办理", "申请", "执行", "操作"
        };
        
        for (String verb : actionVerbs) {
            if (normalized.contains(verb)) {
                return true;
            }
        }
        return false;
    }
}

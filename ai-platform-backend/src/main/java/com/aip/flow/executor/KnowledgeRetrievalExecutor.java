package com.aip.flow.executor;

import com.aip.flow.engine.FlowContext;
import com.aip.flow.engine.NodeResult;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 知识检索节点执行器
 * 从知识库中检索与用户问题相关的内容，为 LLM 提供上下文参考
 * <p>
 * 功能说明：
 * - 支持关键词搜索和向量相似度搜索
 * - 支持指定知识库（kbId）或使用用户消息作为检索词
 * - 可配置返回结果数量（topK）
 * - 检索失败时返回 skip，不会中断流程
 * <p>
 * 使用场景：
 * - 询问公司政策、制度时检索 HR 知识库
 * - 查询产品信息时检索产品知识库
 * - 回答技术问题时检索技术文档
 */
@Slf4j
@Component
public class KnowledgeRetrievalExecutor extends BaseNodeExecutor {

    /**
     * 初始化基础属性
     * 注册为 "knowledge_retrieval" 节点，分类为 "ai"（AI能力节点）
     */
    @PostConstruct
    public void init() {
        initBase("knowledge_retrieval", "知识检索", 
                "从知识库中检索与问题相关的内容，返回最匹配的知识条目", "ai",
                Arrays.asList("查询", "搜索", "检索", "查找", "知识库", "相关"));
    }

    /**
     * 执行知识库检索
     * <p>
     * 配置参数（通过 flowData 传入）：
     * - keyword: 检索关键词（可选，默认使用用户当前消息）
     * - kbId: 知识库ID（可选）
     * - topK: 返回结果数量（可选，默认 5）
     *
     * @param context 流程上下文，包含用户消息
     * @param config 节点配置参数
     * @return 节点执行结果，包含检索到的知识内容
     */
    @Override
    public NodeResult execute(FlowContext context, Map<String, Object> config) {
        try {
            // ========== 1. 解析配置参数 ==========
            // 优先使用配置的关键词，否则使用用户当前消息
            String keyword = (String) config.getOrDefault("keyword", context.getCurrentMessage());
            String kbId = (String) config.getOrDefault("kbId", "");
            int topK = config.containsKey("topK") 
                    ? Integer.parseInt(config.get("topK").toString()) 
                    : 5;

            // ========== 2. 执行知识库检索 ==========
            List<Map<String, Object>> results = searchKnowledge(keyword, kbId, topK);

            // ========== 3. 处理检索结果 ==========
            if (results == null || results.isEmpty()) {
                log.info("知识库检索结果为空: keyword={}", keyword);
                Map<String, Object> params = new HashMap<>();
                params.put("knowledge_found", false);
                // 检索为空时返回 skip，继续流程而不是失败
                return NodeResult.skip("未找到相关知识内容");
            }

            // ========== 4. 构建检索摘要 ==========
            StringBuilder summary = new StringBuilder();
            summary.append("【相关知识】\n\n");
            
            for (int i = 0; i < results.size(); i++) {
                Map<String, Object> item = results.get(i);
                summary.append(String.format("%d. %s\n", i + 1, item.get("title")));
                if (item.containsKey("content")) {
                    String content = item.get("content").toString();
                    // 限制单个结果长度，避免上下文过长
                    if (content.length() > 200) {
                        content = content.substring(0, 200) + "...";
                    }
                    summary.append("   ").append(content).append("\n\n");
                }
            }

            // ========== 5. 构建返回结果 ==========
            Map<String, Object> params = new HashMap<>();
            params.put("knowledge_found", true);
            params.put("knowledge_count", results.size());
            params.put("knowledge_results", results);

            log.info("知识库检索成功: keyword={}, resultCount={}", keyword, results.size());
            return NodeResult.success(summary.toString(), params);

        } catch (Exception e) {
            log.error("知识检索异常: {}", e.getMessage(), e);
            return NodeResult.fail("知识检索失败", "KNOWLEDGE_ERROR");
        }
    }

    /**
     * 执行知识库搜索
     * <p>
     * 注意：当前为模拟实现，实际项目中需调用真实的知识库检索服务
     *
     * @param keyword 检索关键词
     * @param kbId 知识库ID（可为空）
     * @param topK 返回结果数量
     * @return 检索结果列表
     */
    private List<Map<String, Object>> searchKnowledge(String keyword, String kbId, int topK) {
        // 参数校验
        if (keyword == null || keyword.isBlank()) {
            log.warn("检索关键词为空，跳过检索");
            return Collections.emptyList();
        }

        List<Map<String, Object>> results = new ArrayList<>();
        
        // 模拟返回一些知识条目（实际应调用 ES 或其他向量数据库）
        Map<String, Object> result1 = new HashMap<>();
        result1.put("id", "1");
        result1.put("title", "常见问题解答");
        result1.put("content", "关于系统使用的常见问题解答。您可以通过以下方式获取帮助：1. 查看用户手册；2. 联系技术支持；3. 访问官方文档。");
        result1.put("score", 0.95);
        results.add(result1);

        Map<String, Object> result2 = new HashMap<>();
        result2.put("id", "2");
        result2.put("title", "功能介绍");
        result2.put("content", "本系统提供多种 AI 能力，包括智能问答、知识检索、文档处理等功能。您可以根据需要选择合适的模块。");
        result2.put("score", 0.85);
        results.add(result2);

        return results;
    }
}
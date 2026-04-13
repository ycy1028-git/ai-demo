package com.aip.flow.executor;

import com.aip.flow.engine.FlowContext;
import com.aip.flow.engine.NodeResult;
import com.aip.knowledge.dto.KnowledgeSearchQueryDTO;
import com.aip.knowledge.dto.KnowledgeSearchResultDTO;
import com.aip.knowledge.dto.PageResultDTO;
import com.aip.knowledge.service.IKnowledgeSearchService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class KnowledgeRetrievalExecutor extends BaseNodeExecutor {

    private final IKnowledgeSearchService knowledgeSearchService;

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

            // 检索类型：默认优先向量检索
            String searchType = (String) config.getOrDefault("searchType", "vector");

            // ========== 2. 执行知识库检索 ==========
            List<Map<String, Object>> results = searchKnowledge(keyword, kbId, topK, searchType);

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
                summary.append(String.format("%d. %s", i + 1, item.get("title")));
                if (item.get("kbName") != null) {
                    summary.append(String.format("（知识库：%s）", item.get("kbName")));
                }
                summary.append("\n");
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
            params.put("knowledge_summary", summary.toString());
            params.put("knowledge_search_type", searchType);

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
     * 调用知识检索服务执行 ES / 向量检索
     *
     * @param keyword 检索关键词
     * @param kbId 知识库ID（可为空）
     * @param topK 返回结果数量
     * @return 检索结果列表
     */
    private List<Map<String, Object>> searchKnowledge(String keyword, String kbId, int topK, String searchType) {
        // 参数校验
        if (keyword == null || keyword.isBlank()) {
            log.warn("检索关键词为空，跳过检索");
            return Collections.emptyList();
        }

        KnowledgeSearchQueryDTO query = KnowledgeSearchQueryDTO.builder()
                .keyword(keyword)
                .kbId(kbId == null || kbId.isBlank() ? null : kbId)
                .searchType(searchType == null || searchType.isBlank() ? "vector" : searchType)
                .topK(topK)
                .page(1)
                .pageSize(topK)
                .build();

        PageResultDTO<KnowledgeSearchResultDTO> pageResult = knowledgeSearchService.search(query);
        List<KnowledgeSearchResultDTO> records = pageResult != null && pageResult.getRecords() != null
                ? pageResult.getRecords()
                : Collections.emptyList();

        List<Map<String, Object>> results = new ArrayList<>();
        for (KnowledgeSearchResultDTO record : records) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", record.getId());
            item.put("title", record.getTitle());
            item.put("content", record.getContent());
            item.put("summary", record.getSummary());
            item.put("kbId", record.getKbId());
            item.put("kbName", record.getKbName());
            item.put("score", record.getScore());
            results.add(item);
        }
        return results;
    }
}

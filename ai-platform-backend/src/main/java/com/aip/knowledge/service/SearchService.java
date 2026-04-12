package com.aip.knowledge.service;

import com.aip.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 搜索服务
 * 提供统一的搜索接口，整合ES检索和向量搜索能力
 */
@Slf4j
@Service
public class SearchService {

    @Autowired
    private ElasticsearchService elasticsearchService;

    @Autowired
    private EmbeddingService embeddingService;

    @Autowired
    private IKnowledgeBaseService knowledgeBaseService;

    /** 默认返回结果数 */
    private static final int DEFAULT_TOP_K = 5;

    /**
     * 关键词搜索
     *
     * @param kbCode 知识库编码
     * @param keyword 搜索关键词
     * @param topK 返回结果数
     * @return 匹配的文本内容列表
     */
    public List<String> search(String kbCode, String keyword, int topK) {
        if (kbCode == null || kbCode.isBlank()) {
            log.warn("知识库编码为空，跳过搜索");
            return Collections.emptyList();
        }
        if (keyword == null || keyword.isBlank()) {
            log.warn("搜索关键词为空");
            return Collections.emptyList();
        }

        try {
            // 获取知识库的ES索引名称
            String esIndex = knowledgeBaseService.getEsIndexByCode(kbCode);
            if (esIndex == null || esIndex.isBlank()) {
                log.warn("未找到知识库对应的ES索引: {}", kbCode);
                return Collections.emptyList();
            }

            // 执行全文搜索
            List<String> results = elasticsearchService.searchByKeyword(esIndex, keyword, topK);
            log.info("关键词搜索完成: kbCode={}, keyword={}, 结果数={}", kbCode, keyword, results.size());
            return results;

        } catch (Exception e) {
            log.error("搜索失败: kbCode={}, keyword={}", kbCode, keyword, e);
            return Collections.emptyList();
        }
    }

    /**
     * 关键词搜索（使用默认结果数）
     */
    public List<String> search(String kbCode, String keyword) {
        return search(kbCode, keyword, DEFAULT_TOP_K);
    }

    /**
     * 向量相似度搜索
     *
     * @param kbCode 知识库编码
     * @param queryText 查询文本
     * @param topK 返回结果数
     * @return 相似度最高的文本内容列表
     */
    public List<String> similaritySearch(String kbCode, String queryText, int topK) {
        if (kbCode == null || kbCode.isBlank()) {
            log.warn("知识库编码为空，跳过向量搜索");
            return Collections.emptyList();
        }
        if (queryText == null || queryText.isBlank()) {
            log.warn("查询文本为空");
            return Collections.emptyList();
        }

        try {
            // 获取知识库的ES索引名称
            String esIndex = knowledgeBaseService.getEsIndexByCode(kbCode);
            if (esIndex == null || esIndex.isBlank()) {
                log.warn("未找到知识库对应的ES索引: {}", kbCode);
                return Collections.emptyList();
            }

            // 将查询文本转换为向量
            float[] queryVector = embeddingService.embed(queryText);

            // 执行向量搜索
            List<String> results = elasticsearchService.similaritySearch(esIndex, queryVector, topK);
            log.info("向量搜索完成: kbCode={}, 文本长度={}, 结果数={}", kbCode, queryText.length(), results.size());
            return results;

        } catch (Exception e) {
            log.error("向量搜索失败: kbCode={}, queryText={}", kbCode, queryText, e);
            return Collections.emptyList();
        }
    }

    /**
     * 向量相似度搜索（使用默认结果数）
     */
    public List<String> similaritySearch(String kbCode, String queryText) {
        return similaritySearch(kbCode, queryText, DEFAULT_TOP_K);
    }

    /**
     * 混合搜索（关键词 + 向量）
     *
     * @param kbCode 知识库编码
     * @param queryText 查询文本
     * @param topK 返回结果数
     * @return 搜索结果列表
     */
    public List<String> hybridSearch(String kbCode, String queryText, int topK) {
        List<String> keywordResults = search(kbCode, queryText, topK);
        List<String> vectorResults = similaritySearch(kbCode, queryText, topK);

        // 合并去重
        List<String> merged = new ArrayList<>(keywordResults);
        for (String result : vectorResults) {
            if (!merged.contains(result)) {
                merged.add(result);
            }
        }

        // 截取topK
        if (merged.size() > topK) {
            merged = merged.subList(0, topK);
        }

        return merged;
    }

    /**
     * 混合搜索（使用ES索引）
     *
     * @param esIndex ES索引名
     * @param queryText 查询文本
     * @param topK 返回结果数
     * @return 搜索结果列表
     */
    public List<String> hybridSearchByIndex(String esIndex, String queryText, int topK) {
        if (esIndex == null || esIndex.isBlank()) {
            log.warn("ES索引名为空");
            return Collections.emptyList();
        }
        if (queryText == null || queryText.isBlank()) {
            log.warn("查询文本为空");
            return Collections.emptyList();
        }

        try {
            // 关键词搜索
            List<String> keywordResults = elasticsearchService.searchByKeyword(esIndex, queryText, topK);

            // 向量搜索
            List<String> vectorResults;
            try {
                float[] vector = embeddingService.embed(queryText);
                vectorResults = elasticsearchService.similaritySearch(esIndex, vector, topK);
            } catch (Exception e) {
                log.warn("向量搜索失败，使用关键词搜索结果: {}", e.getMessage());
                vectorResults = Collections.emptyList();
            }

            // 合并去重
            List<String> merged = new ArrayList<>(keywordResults);
            for (String result : vectorResults) {
                if (!merged.contains(result)) {
                    merged.add(result);
                }
            }

            // 截取topK
            if (merged.size() > topK) {
                merged = merged.subList(0, topK);
            }

            return merged;

        } catch (Exception e) {
            log.error("混合搜索失败: esIndex={}, queryText={}", esIndex, queryText, e);
            return Collections.emptyList();
        }
    }

    /**
     * 多知识库搜索
     *
     * @param kbCodes 知识库编码列表
     * @param keyword 搜索关键词
     * @param topK 每个知识库返回的结果数
     * @return 所有知识库的搜索结果
     */
    public List<String> multiSearch(List<String> kbCodes, String keyword, int topK) {
        if (kbCodes == null || kbCodes.isEmpty()) {
            log.warn("知识库编码列表为空");
            return Collections.emptyList();
        }

        List<String> results = new ArrayList<>();
        for (String kbCode : kbCodes) {
            List<String> kbResults = search(kbCode, keyword, topK);
            results.addAll(kbResults);
        }

        log.info("多知识库搜索完成: kbCount={}, keyword={}, 总结果数={}", kbCodes.size(), keyword, results.size());
        return results;
    }
}

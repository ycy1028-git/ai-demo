package com.aip.knowledge.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.MatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.json.JsonData;
import com.aip.common.exception.BusinessException;
import com.aip.knowledge.config.ElasticsearchIndexConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.IndexQuery;
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Elasticsearch索引管理服务
 */
@Slf4j
@Service
public class ElasticsearchService {

    @Autowired
    private ElasticsearchTemplate elasticsearchTemplate;

    @Autowired
    private ElasticsearchIndexConfig indexConfig;

    @Autowired
    private ElasticsearchClient elasticsearchClient;

    /**
     * 创建知识库索引
     *
     * @param indexName 索引名称
     * @return 是否创建成功
     */
    public boolean createIndex(String indexName) {
        try {
            IndexCoordinates indexCoordinates = IndexCoordinates.of(indexName);
            IndexOperations indexOps = elasticsearchTemplate.indexOps(indexCoordinates);

            if (indexOps.exists()) {
                log.info("索引已存在: {}", indexName);
                return true;
            }

            // 创建索引配置
            Map<String, Object> settings = new HashMap<>();
            settings.put("number_of_shards", indexConfig.getShards());
            settings.put("number_of_replicas", indexConfig.getReplicas());

            indexOps.create(settings);
            // 跳过putMapping，使用默认映射
            // 完整实现需要在ES端手动配置映射或使用@Document注解

            log.info("索引创建成功: {}", indexName);
            return true;
        } catch (Exception e) {
            log.error("创建索引失败: {}", indexName, e);
            throw new BusinessException("创建索引失败: " + e.getMessage());
        }
    }

    /**
     * 删除索引
     *
     * @param indexName 索引名称
     * @return 是否删除成功
     */
    public boolean deleteIndex(String indexName) {
        try {
            IndexCoordinates indexCoordinates = IndexCoordinates.of(indexName);
            IndexOperations indexOps = elasticsearchTemplate.indexOps(indexCoordinates);

            if (!indexOps.exists()) {
                log.info("索引不存在: {}", indexName);
                return true;
            }

            indexOps.delete();
            log.info("索引删除成功: {}", indexName);
            return true;
        } catch (Exception e) {
            log.error("删除索引失败: {}", indexName, e);
            throw new BusinessException("删除索引失败: " + e.getMessage());
        }
    }

    /**
     * 检查索引是否存在
     *
     * @param indexName 索引名称
     * @return 是否存在
     */
    public boolean indexExists(String indexName) {
        IndexCoordinates indexCoordinates = IndexCoordinates.of(indexName);
        IndexOperations indexOps = elasticsearchTemplate.indexOps(indexCoordinates);
        return indexOps.exists();
    }

    /**
     * 索引文档
     *
     * @param indexName 索引名称
     * @param id        文档ID
     * @param document  文档内容（Map结构）
     * @return 索引ID
     */
    public String indexDocument(String indexName, String id, Map<String, Object> document) {
        try {
            document.put("id", id);
            IndexQuery indexQuery = new IndexQueryBuilder()
                    .withId(id)
                    .withObject(document)
                    .build();

            String indexedId = elasticsearchTemplate.index(indexQuery, IndexCoordinates.of(indexName));
            log.debug("文档索引成功: {} -> {}", id, indexName);
            return indexedId;
        } catch (Exception e) {
            log.error("索引文档失败: {}", id, e);
            throw new BusinessException("索引文档失败: " + e.getMessage());
        }
    }

    /**
     * 批量索引文档
     *
     * @param indexName 索引名称
     * @param documents 文档列表
     * @return 索引文档数
     */
    public int bulkIndex(String indexName, List<Map<String, Object>> documents) {
        if (documents == null || documents.isEmpty()) {
            return 0;
        }

        try {
            List<IndexQuery> queries = new ArrayList<>();
            for (Map<String, Object> doc : documents) {
                String id = UUID.randomUUID().toString();
                doc.put("id", id);
                queries.add(new IndexQueryBuilder()
                        .withId(id)
                        .withObject(doc)
                        .build());
            }

            elasticsearchTemplate.bulkIndex(queries, IndexCoordinates.of(indexName));
            log.info("批量索引文档成功: {} 条 -> {}", documents.size(), indexName);
            return documents.size();
        } catch (Exception e) {
            log.error("批量索引文档失败", e);
            throw new BusinessException("批量索引文档失败: " + e.getMessage());
        }
    }

    /**
     * 删除文档
     *
     * @param indexName 索引名称
     * @param docId     文档ID
     * @return 是否删除成功
     */
    public boolean deleteDocument(String indexName, String docId) {
        try {
            elasticsearchTemplate.delete(docId, IndexCoordinates.of(indexName));
            log.debug("删除文档成功: {} <- {}", docId, indexName);
            return true;
        } catch (Exception e) {
            log.error("删除文档失败: {}", docId, e);
            return false;
        }
    }

    /**
     * 全文搜索（关键词搜索）
     *
     * @param indexName 索引名称
     * @param keyword 搜索关键词
     * @param topK 返回条数
     * @return 匹配的文档内容列表
     */
    public List<String> search(String indexName, String keyword, int topK) {
        return searchByKeyword(indexName, keyword, topK);
    }

    /**
     * 关键词搜索（通过content字段）
     *
     * @param indexName 索引名称
     * @param keyword 搜索关键词
     * @param topK 返回条数
     * @return 匹配的文档内容列表
     */
    public List<String> searchByKeyword(String indexName, String keyword, int topK) {
        return searchByKeywordRaw(indexName, keyword, topK).stream()
                .map(this::extractContent)
                .collect(Collectors.toList());
    }

    /**
     * 关键词搜索，返回完整文档Map
     *
     * @param indexName 索引名称
     * @param keyword 搜索关键词
     * @param topK 返回条数
     * @return 匹配的文档Map列表
     */
    public List<Map<String, Object>> searchByKeywordRaw(String indexName, String keyword, int topK) {
        if (keyword == null || keyword.isBlank()) {
            return Collections.emptyList();
        }

        try {
            Query matchQuery = MatchQuery.of(m -> m
                    .field("content")
                    .query(keyword)
            )._toQuery();

            SearchRequest searchRequest = SearchRequest.of(s -> s
                    .index(indexName)
                    .query(matchQuery)
                    .size(topK)
            );

            SearchResponse<Map> response = elasticsearchClient.search(searchRequest, Map.class);

            return response.hits().hits().stream()
                    .map(Hit::source)
                    .filter(Objects::nonNull)
                    .map(m -> (Map<String, Object>) m)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("关键词搜索失败: index={}, keyword={}", indexName, keyword, e);
            return Collections.emptyList();
        }
    }

    /**
     * 向量相似度搜索
     *
     * @param indexName 索引名称
     * @param vector 查询向量
     * @param topK 返回条数
     * @return 相似度最高的文档内容列表
     */
    public List<String> similaritySearch(String indexName, float[] vector, int topK) {
        return similaritySearchRaw(indexName, vector, topK).stream()
                .map(this::extractContent)
                .collect(Collectors.toList());
    }

    /**
     * 向量相似度搜索，返回完整文档Map
     *
     * @param indexName 索引名称
     * @param vector 查询向量
     * @param topK 返回条数
     * @return 相似度最高的文档Map列表
     */
    public List<Map<String, Object>> similaritySearchRaw(String indexName, float[] vector, int topK) {
        if (vector == null || vector.length == 0) {
            return Collections.emptyList();
        }

        try {
            SearchRequest searchRequest = SearchRequest.of(s -> s
                    .index(indexName)
                    .knn(k -> k
                            .field("vector")
                            .queryVector(floatsToFloats(vector))
                            .k(topK)
                            .numCandidates(topK * 2) // Elasticsearch 8.x 要求此参数
                            .boost(1.0f)
                    )
            );

            SearchResponse<Map> response = elasticsearchClient.search(searchRequest, Map.class);

            return response.hits().hits().stream()
                    .map(Hit::source)
                    .filter(Objects::nonNull)
                    .map(m -> (Map<String, Object>) m)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("向量搜索失败: index={}, topK={}", indexName, topK, e);
            return Collections.emptyList();
        }
    }

    /**
     * 从文档Map中提取content字段
     */
    @SuppressWarnings("unchecked")
    private String extractContent(Map<String, Object> source) {
        Object content = source.get("content");
        if (content != null) {
            return content.toString();
        }
        // 如果没有content字段，返回整个文档的字符串表示
        Object title = source.get("title");
        if (title != null) {
            return title + ": " + content;
        }
        return source.toString();
    }

    /**
     * float数组转Float列表
     */
    private List<Float> floatsToFloats(float[] floats) {
        List<Float> result = new ArrayList<>(floats.length);
        for (float f : floats) {
            result.add(f);
        }
        return result;
    }

    /**
     * 生成索引映射配置
     */
    private Map<String, Object> generateMapping() {
        Map<String, Object> mapping = new HashMap<>();
        Map<String, Object> properties = new HashMap<>();

        // ID字段
        properties.put("id", Map.of("type", "keyword"));

        // 知识库ID
        properties.put("kbId", Map.of("type", "long"));

        // 标题
        properties.put("title", Map.of(
                "type", "text",
                "analyzer", "ik_max_word",
                "fields", Map.of("keyword", Map.of("type", "keyword"))
        ));

        // 内容
        properties.put("content", Map.of(
                "type", "text",
                "analyzer", "ik_max_word"
        ));

        // 摘要
        properties.put("summary", Map.of("type", "text"));

        // 向量字段
        properties.put("vector", Map.of(
                "type", "dense_vector",
                "dims", indexConfig.getVectorDimension(),
                "index", true,
                "similarity", "cosine"
        ));

        // 标签
        properties.put("tags", Map.of("type", "keyword"));

        // 状态
        properties.put("status", Map.of("type", "integer"));

        // 创建时间
        properties.put("createdAt", Map.of("type", "date"));

        mapping.put("properties", properties);
        return mapping;
    }
}

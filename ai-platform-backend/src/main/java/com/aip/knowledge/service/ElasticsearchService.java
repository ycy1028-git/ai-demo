package com.aip.knowledge.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.MatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.aip.common.exception.BusinessException;
import com.aip.knowledge.config.ElasticsearchIndexConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.IndexQuery;
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder;
import org.springframework.stereotype.Service;

import java.lang.reflect.Array;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Elasticsearch索引管理服务
 */
@Slf4j
@Service
public class ElasticsearchService {

    private static final String VECTOR_FIELD_NAME = "vector";

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
                log.info("索引已存在: {}，检查向量字段", indexName);
                ensureVectorFieldMapping(indexName, indexOps);
                return true;
            }

            Map<String, Object> settings = new HashMap<>();
            settings.put("number_of_shards", indexConfig.getShards());
            settings.put("number_of_replicas", indexConfig.getReplicas());

            indexOps.create(settings);
            Document mappingDoc = Document.from(generateMapping());
            indexOps.putMapping(mappingDoc);

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

    private void ensureVectorFieldMapping(String indexName, IndexOperations indexOps) {
        try {
            Map<String, Object> mapping = indexOps.getMapping();
            Integer dims = extractVectorDimensionFromMapping(mapping);
            if (dims != null && dims > 0) {
                return;
            }

            Document vectorMappingDoc = Document.from(Map.of(
                    "properties", Map.of(
                            VECTOR_FIELD_NAME, Map.of(
                                    "type", "dense_vector",
                                    "dims", indexConfig.getVectorDimension(),
                                    "index", true,
                                    "similarity", "cosine"
                            )
                    )
            ));

            indexOps.putMapping(vectorMappingDoc);
            log.info("向量字段映射补充完成: {}", indexName);
        } catch (Exception e) {
            log.warn("补充向量映射失败: {}", indexName, e);
        }
    }
    public Integer getIndexVectorDimension(String indexName) {
        if (indexName == null || indexName.isBlank() || !indexExists(indexName)) {
            return null;
        }

        try {
            IndexOperations indexOps = elasticsearchTemplate.indexOps(IndexCoordinates.of(indexName));
            Map<String, Object> mapping = indexOps.getMapping();
            Integer dims = extractVectorDimensionFromMapping(mapping);
            if (dims == null) {
                log.warn("索引未找到向量维度映射: {}", indexName);
            }
            return dims;
        } catch (Exception e) {
            log.warn("读取索引向量维度失败: {}", indexName, e);
            return null;
        }
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
            log.error("批量索引文档失败: index={}", indexName, e);

            String vectorDimensionError = buildVectorDimensionMismatchMessage(indexName, documents, e);
            if (vectorDimensionError != null) {
                throw new BusinessException(vectorDimensionError, e);
            }

            throw new BusinessException("批量索引文档失败: " + e.getMessage(), e);
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
     * 按知识条目ID删除已索引的分块文档
     *
     * @param indexName 索引名称
     * @param itemId 知识条目ID
     * @return 删除文档数
     */
    public int deleteDocumentsByItemId(String indexName, String itemId) {
        if (itemId == null || itemId.isBlank() || !indexExists(indexName)) {
            return 0;
        }

        try {
            Query matchQuery = MatchQuery.of(m -> m
                    .field("itemId")
                    .query(itemId)
            )._toQuery();

            SearchRequest searchRequest = SearchRequest.of(s -> s
                    .index(indexName)
                    .query(matchQuery)
                    .size(1000)
            );

            SearchResponse<Map> response = elasticsearchClient.search(searchRequest, Map.class);
            List<String> docIds = response.hits().hits().stream()
                    .map(Hit::id)
                    .filter(Objects::nonNull)
                    .toList();

            for (String docId : docIds) {
                elasticsearchTemplate.delete(docId, IndexCoordinates.of(indexName));
            }

            if (!docIds.isEmpty()) {
                log.info("按知识条目删除索引文档成功: itemId={}, count={}, index={}", itemId, docIds.size(), indexName);
            }
            return docIds.size();
        } catch (Exception e) {
            log.error("按知识条目删除索引文档失败: itemId={}, index={}", itemId, indexName, e);
            throw new BusinessException("删除旧索引失败: " + e.getMessage());
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
            Query matchQuery = Query.of(q -> q
                    .bool(b -> b
                            .should(s -> s.match(m -> m.field("title").query(keyword)))
                            .should(s -> s.match(m -> m.field("content").query(keyword)))
                            .should(s -> s.match(m -> m.field("file.content").query(keyword)))
                            .minimumShouldMatch("1")
                    )
            );

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

        Integer indexDimension = getIndexVectorDimension(indexName);
        if (indexDimension != null && indexDimension > 0 && vector.length != indexDimension) {
            throw new BusinessException(String.format(
                    "向量检索维度不匹配: index=%s, mapping=%d, query=%d。请重建对应知识库索引后重试",
                    indexName,
                    indexDimension,
                    vector.length
            ));
        }

        try {
            SearchRequest searchRequest = SearchRequest.of(s -> s
                    .index(indexName)
                    .knn(k -> k
                            .field(VECTOR_FIELD_NAME)
                            .queryVector(floatsToFloats(vector))
                            .k(topK)
                            .numCandidates(topK * 2)
                            .boost(1.0f)
                    )
            );

            SearchResponse<Map> response = elasticsearchClient.search(searchRequest, Map.class);

            return response.hits().hits().stream()
                    .map(hit -> {
                        Map<String, Object> doc = new HashMap<>();
                        Map<String, Object> source = hit.source();
                        if (source != null) {
                            doc.putAll(source);
                        }
                        if (hit.score() != null) {
                            doc.put("score", hit.score());
                        }
                        return doc;
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            String vectorDimensionError = buildVectorDimensionMismatchMessage(
                    indexName,
                    Collections.singletonList(Map.of(VECTOR_FIELD_NAME, floatsToFloats(vector))),
                    e
            );
            if (vectorDimensionError != null) {
                throw new BusinessException(vectorDimensionError, e);
            }

            log.error("向量搜索失败: index={}, topK={}", indexName, topK, e);
            return Collections.emptyList();
        }
    }

    public List<Map<String, Object>> similaritySearchAcrossIndexes(List<String> indexes, float[] vector, int topK) {
        if (indexes == null || indexes.isEmpty() || vector == null || vector.length == 0) {
            return Collections.emptyList();
        }

        validateVectorDimensionsAcrossIndexes(indexes, vector.length);

        try {
            SearchRequest searchRequest = SearchRequest.of(s -> s
                    .index(indexes)
                    .knn(k -> k
                            .field(VECTOR_FIELD_NAME)
                            .queryVector(floatsToFloats(vector))
                            .k(topK)
                            .numCandidates(topK * 2)
                            .boost(1.0f)
                    )
            );

            SearchResponse<Map> response = elasticsearchClient.search(searchRequest, Map.class);

            return response.hits().hits().stream()
                    .map(hit -> {
                        Map<String, Object> doc = new HashMap<>();
                        Map<String, Object> source = hit.source();
                        if (source != null) {
                            doc.putAll(source);
                        }
                        doc.put("_index", hit.index());
                        if (hit.score() != null) {
                            doc.put("score", hit.score());
                        }
                        if (hit.id() != null) {
                            doc.put("id", hit.id());
                        }
                        return doc;
                    })
                    .collect(Collectors.toList());

        } catch (Exception e) {
            String vectorDimensionError = buildVectorDimensionMismatchMessage(indexes, vector, e);
            if (vectorDimensionError != null) {
                throw new BusinessException(vectorDimensionError, e);
            }

            log.error("向量搜索失败: indexes={}, topK={}", indexes, topK, e);
            return Collections.emptyList();
        }
    }
    private void validateVectorDimensionsAcrossIndexes(List<String> indexes, int vectorLength) {
        if (indexes == null || indexes.isEmpty()) {
            throw new BusinessException("索引列表不能为空");
        }
        if (vectorLength <= 0) {
            throw new BusinessException("查询向量维度必须大于0");
        }

        for (String index : indexes) {
            Integer indexDimension = getIndexVectorDimension(index);
            if (indexDimension == null || indexDimension <= 0) {
                throw new BusinessException(String.format(
                        "索引未找到向量维度映射: %s。请先重建索引或确保向量映射存在",
                        index
                ));
            }
            if (indexDimension != vectorLength) {
                throw new BusinessException(String.format(
                        "向量维度不匹配: index=%s, mapping=%d, query=%d。请重建对应知识库索引后重试",
                        index,
                        indexDimension,
                        vectorLength
                ));
            }
        }
    }

    private String buildVectorDimensionMismatchMessage(List<String> indexes, float[] vector, Exception exception) {
        for (String index : indexes) {
            String message = buildVectorDimensionMismatchMessage(
                    index,
                    Collections.singletonList(Map.of(VECTOR_FIELD_NAME, floatsToFloats(vector))),
                    exception
            );
            if (message != null) {
                return message;
            }
        }
        return null;
    }


    /**
     * 从文档Map中提取content字段
     */
    @SuppressWarnings("unchecked")
    private String extractContent(Map<String, Object> source) {
        Object content = source.get("content");
        if (content != null && !content.toString().isBlank()) {
            return content.toString();
        }
        Object file = source.get("file");
        if (file instanceof Map<?, ?> fileMap) {
            Object fileContent = fileMap.get("content");
            if (fileContent != null && !fileContent.toString().isBlank()) {
                return fileContent.toString();
            }
        }
        // 如果没有content字段，返回整个文档的字符串表示
        Object title = source.get("title");
        if (title != null) {
            return title.toString();
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
     * 从mapping结构中解析向量维度
     */
    private Integer extractVectorDimensionFromMapping(Object node) {
        if (node == null) {
            return null;
        }

        if (node instanceof Map<?, ?> mapNode) {
            Object vectorNode = mapNode.get(VECTOR_FIELD_NAME);
            Integer dims = extractVectorDimensionFromVectorNode(vectorNode);
            if (dims != null) {
                return dims;
            }

            for (Object value : mapNode.values()) {
                Integer nestedDims = extractVectorDimensionFromMapping(value);
                if (nestedDims != null) {
                    return nestedDims;
                }
            }
            return null;
        }

        if (node instanceof Iterable<?> iterableNode) {
            for (Object value : iterableNode) {
                Integer nestedDims = extractVectorDimensionFromMapping(value);
                if (nestedDims != null) {
                    return nestedDims;
                }
            }
        }

        return null;
    }

    private Integer extractVectorDimensionFromVectorNode(Object vectorNode) {
        if (!(vectorNode instanceof Map<?, ?> vectorMap)) {
            return null;
        }

        Object dimsValue = vectorMap.get("dims");
        if (dimsValue == null) {
            return null;
        }

        if (dimsValue instanceof Number number) {
            return number.intValue();
        }

        if (dimsValue instanceof String value) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException ignored) {
                log.warn("无法解析vector.dims: {}", value);
            }
        }

        return null;
    }

    private String buildVectorDimensionMismatchMessage(String indexName, List<Map<String, Object>> documents, Exception exception) {
        if (!isVectorDimensionMismatchError(exception)) {
            return null;
        }

        Integer indexDimension = getIndexVectorDimension(indexName);
        Integer documentDimension = extractDocumentVectorDimension(documents);
        String kbId = extractKbId(documents);
        String rebuildHint = buildRebuildHint(kbId);

        if (indexDimension != null && documentDimension != null) {
            return String.format(
                    "向量维度不匹配: index=%s, mapping=%d, embedding=%d。请先重建知识库索引（%s）后再重试",
                    indexName,
                    indexDimension,
                    documentDimension,
                    rebuildHint
            );
        }

        return String.format(
                "向量维度不匹配: index=%s。请检查 embedding 维度与索引 mapping.dims 是否一致，并重建知识库索引（%s）后重试。原始错误: %s",
                indexName,
                rebuildHint,
                collectExceptionMessages(exception)
        );
    }

    private boolean isVectorDimensionMismatchError(Throwable throwable) {
        String message = collectExceptionMessages(throwable).toLowerCase(Locale.ROOT);
        return message.contains("dense_vector") && message.contains("dimension");
    }

    private Integer extractDocumentVectorDimension(List<Map<String, Object>> documents) {
        for (Map<String, Object> document : documents) {
            Object vector = document.get(VECTOR_FIELD_NAME);
            if (vector instanceof Collection<?> collection) {
                return collection.size();
            }
            if (vector != null && vector.getClass().isArray()) {
                return Array.getLength(vector);
            }
        }
        return null;
    }

    private String extractKbId(List<Map<String, Object>> documents) {
        for (Map<String, Object> document : documents) {
            Object kbId = document.get("kbId");
            if (kbId != null && !kbId.toString().isBlank()) {
                return kbId.toString();
            }
        }
        return null;
    }

    private String buildRebuildHint(String kbId) {
        if (kbId != null && !kbId.isBlank()) {
            return "/kb/knowledge-base/" + kbId + "/rebuild-index";
        }
        return "/kb/knowledge-base/{id}/rebuild-index";
    }

    private String collectExceptionMessages(Throwable throwable) {
        StringBuilder builder = new StringBuilder();
        Throwable current = throwable;
        int depth = 0;
        while (current != null && depth < 5) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                if (builder.length() > 0) {
                    builder.append(" | ");
                }
                builder.append(current.getMessage());
            }
            current = current.getCause();
            depth++;
        }
        return builder.toString();
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
        properties.put("kbId", Map.of("type", "keyword"));

        // 知识条目ID
        properties.put("itemId", Map.of("type", "keyword"));

        String analyzer = indexConfig.getTextAnalyzer();
        if (analyzer == null || analyzer.isBlank()) {
            analyzer = "standard";
        }

        // 标题
        properties.put("title", Map.of(
                "type", "text",
                "analyzer", analyzer,
                "fields", Map.of("keyword", Map.of("type", "keyword"))
        ));

        // 内容
        properties.put("content", Map.of(
                "type", "text",
                "analyzer", analyzer
        ));

        properties.put("file", Map.of(
                "properties", Map.of(
                        "docId", Map.of("type", "keyword"),
                        "name", Map.of("type", "keyword"),
                        "type", Map.of("type", "keyword"),
                        "content", Map.of("type", "text", "analyzer", analyzer)
                )
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

        properties.put("sourceType", Map.of("type", "keyword"));
        properties.put("sourceDocId", Map.of("type", "keyword"));
        properties.put("originalFileName", Map.of("type", "keyword"));
        properties.put("fileType", Map.of("type", "keyword"));
        properties.put("chunkSource", Map.of("type", "keyword"));

        // 状态
        properties.put("status", Map.of("type", "integer"));

        // 分块信息
        properties.put("chunkIndex", Map.of("type", "integer"));
        properties.put("totalChunks", Map.of("type", "integer"));

        // 创建时间
        properties.put("createdAt", Map.of("type", "date"));

        mapping.put("properties", properties);
        return mapping;
    }
}

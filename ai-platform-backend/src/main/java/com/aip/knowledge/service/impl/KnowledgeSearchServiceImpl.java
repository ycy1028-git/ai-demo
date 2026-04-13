package com.aip.knowledge.service.impl;

import com.aip.app.service.IInvocationStatService;
import com.aip.common.exception.BusinessException;
import com.aip.knowledge.dto.*;
import com.aip.knowledge.entity.Document;
import com.aip.knowledge.entity.KnowledgeBase;
import com.aip.knowledge.entity.KnowledgeItem;
import com.aip.knowledge.service.*;
import com.aip.knowledge.mapper.KnowledgeItemMapper;
import com.aip.knowledge.mapper.KnowledgeBaseMapper;
import com.aip.knowledge.mapper.DocumentMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 知识检索服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeSearchServiceImpl implements IKnowledgeSearchService {

    private final ElasticsearchService elasticsearchService;
    private final EmbeddingService embeddingService;
    private final IKnowledgeBaseService knowledgeBaseService;
    private final IInvocationStatService invocationStatService;
    private final KnowledgeItemMapper knowledgeItemMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final DocumentMapper documentMapper;
    private final ObjectMapper objectMapper;

    @Value("${minio.endpoint:http://localhost:9000}")
    private String minioEndpoint;

    @Value("${minio.bucket:ai-platform}")
    private String minioBucket;

    @Value("${minio.access-key:minioadmin}")
    private String minioAccessKey;

    @Value("${minio.secret-key:minioadmin}")
    private String minioSecretKey;

    private static final int DEFAULT_TOP_K = 5;

    @Override
    public PageResultDTO<KnowledgeSearchResultDTO> search(KnowledgeSearchQueryDTO query) {
        if (query.getKeyword() == null || query.getKeyword().isBlank()) {
            throw new BusinessException("搜索关键词不能为空");
        }

        String searchType = query.getSearchType() != null ? query.getSearchType() : "hybrid";
        int topK = query.getTopK() != null ? query.getTopK() : DEFAULT_TOP_K;
        int page = query.getPage() != null && query.getPage() > 0 ? query.getPage() : 1;
        int pageSize = query.getPageSize() != null && query.getPageSize() > 0 ? query.getPageSize() : 10;

        List<KnowledgeSearchResultDTO> allResults;

        // 确定要搜索的知识库
        List<KnowledgeBase> knowledgeBases = determineKnowledgeBases(query.getKbId());

        if (knowledgeBases.isEmpty()) {
            return PageResultDTO.of(Collections.emptyList(), 0, page, pageSize);
        }

        boolean searchAll = shouldSearchAll(query.getKbId());

        // 根据搜索类型执行搜索
        allResults = switch (searchType) {
            case "keyword" -> performKeywordSearch(knowledgeBases, query.getKeyword(), topK);
            case "vector" -> searchByVector(knowledgeBases, query.getKeyword(), topK, searchAll);
            case "hybrid" -> performHybridSearch(knowledgeBases, query.getKeyword(), topK, searchAll);
            default -> performHybridSearch(knowledgeBases, query.getKeyword(), topK, searchAll);
        };



        // 解析标签
        allResults.forEach(this::parseTags);

        // 分页
        int total = allResults.size();
        int fromIndex = (page - 1) * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, total);

        List<KnowledgeSearchResultDTO> pagedResults;
        if (fromIndex >= total) {
            pagedResults = Collections.emptyList();
        } else {
            pagedResults = allResults.subList(fromIndex, toIndex);
        }

        return PageResultDTO.of(pagedResults, total, page, pageSize);
    }

    /**
     * 确定要搜索的知识库
     */
    private List<KnowledgeBase> determineKnowledgeBases(String kbId) {
        String normalizedKbId = kbId != null ? kbId.trim() : null;
        boolean searchAll = shouldSearchAll(kbId);
        if (!searchAll) {
            KnowledgeBase kb = knowledgeBaseService.getById(normalizedKbId);

            return kb != null ? Collections.singletonList(kb) : Collections.emptyList();
        }

        List<KnowledgeBase> enabledKnowledgeBases = knowledgeBaseService.list().stream()
                .filter(kb -> kb.getStatus() != null && kb.getStatus() == 1)
                .collect(Collectors.toList());

        return enabledKnowledgeBases;
    }

    private List<KnowledgeSearchResultDTO> performKeywordSearch(List<KnowledgeBase> knowledgeBases,
            String keyword, int topK) {
        List<KnowledgeSearchResultDTO> results = new ArrayList<>();
        for (KnowledgeBase kb : knowledgeBases) {
            ensureKnowledgeBaseIndex(kb);
            results.addAll(searchByKeyword(kb, keyword, topK));
        }
        results.sort((a, b) -> Double.compare(
                b.getScore() != null ? b.getScore() : 0,
                a.getScore() != null ? a.getScore() : 0
        ));
        return results;
    }

    private List<KnowledgeSearchResultDTO> searchByKeyword(KnowledgeBase kb, String keyword, int topK) {
        if (kb == null) {
            return Collections.emptyList();
        }
        String esIndex = kb.getEsIndex();
        if (esIndex == null || esIndex.isBlank()) {
            return Collections.emptyList();
        }
        try {
            List<Map<String, Object>> esResults = elasticsearchService.searchByKeywordRaw(
                    esIndex,
                    keyword,
                    topK
            );
            return convertEsResultsToDto(esResults, kb);
        } catch (Exception e) {
            log.error("关键词搜索失败: kb={}, error={}", kb.getName(), e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<KnowledgeSearchResultDTO> performHybridSearch(
            List<KnowledgeBase> knowledgeBases, String keyword, int topK, boolean searchAll) {
        List<KnowledgeSearchResultDTO> keywordResults = performKeywordSearch(knowledgeBases, keyword, topK);
        List<KnowledgeSearchResultDTO> vectorResults = searchByVector(knowledgeBases, keyword, topK, searchAll);

        Map<String, KnowledgeSearchResultDTO> merged = new LinkedHashMap<>();
        keywordResults.forEach(result -> {
            result.setScore(normalizeScore(result.getScore(), "keyword"));
            merged.put(result.getId(), result);
        });
        vectorResults.forEach(result -> {
            result.setScore(normalizeScore(result.getScore(), "vector"));
            merged.merge(result.getId(), result, (existing, newOne) -> {
                double avgScore = (existing.getScore() + newOne.getScore()) / 2;
                existing.setScore(avgScore);
                return existing;
            });
        });

        return new ArrayList<>(merged.values());
    }

    private List<KnowledgeSearchResultDTO> searchByVector(
            List<KnowledgeBase> knowledgeBases, String queryText, int topK, boolean searchAll) {
        if (knowledgeBases.isEmpty()) {
            return Collections.emptyList();
        }

        if (searchAll && knowledgeBases.size() > 1) {
            ensureKnowledgeBasesIndexed(knowledgeBases);
            return searchByVectorAcrossIndexes(knowledgeBases, queryText, topK);
        }

        List<KnowledgeSearchResultDTO> results = new ArrayList<>();
        for (KnowledgeBase kb : knowledgeBases) {
            ensureKnowledgeBaseIndex(kb);
            results.addAll(searchByVectorForKb(kb, queryText, topK));
        }
        return results;
    }

    private List<KnowledgeSearchResultDTO> searchByVectorForKb(KnowledgeBase kb, String queryText, int topK) {
        try {
            float[] vector = embeddingService.embed(queryText);
            List<Map<String, Object>> esResults = elasticsearchService.similaritySearchRaw(kb.getEsIndex(), vector, topK);
            return convertEsResultsToDto(esResults, kb);
        } catch (Exception e) {
            log.error("向量搜索失败: kb={}, error={}", kb.getName(), e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<KnowledgeSearchResultDTO> searchByVectorAcrossIndexes(
            List<KnowledgeBase> knowledgeBases, String queryText, int topK) {
        float[] vector = embeddingService.embed(queryText);
        List<String> indexes = knowledgeBases.stream()
                .map(KnowledgeBase::getEsIndex)
                .filter(esIndex -> esIndex != null && !esIndex.isBlank())
                .distinct()
                .toList();
        if (indexes.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, KnowledgeBase> indexMap = knowledgeBases.stream()
                .filter(kb -> kb.getEsIndex() != null && !kb.getEsIndex().isBlank())
                .collect(Collectors.toMap(KnowledgeBase::getEsIndex, kb -> kb, (a, b) -> a));
        Map<String, KnowledgeBase> idMap = knowledgeBases.stream()
                .collect(Collectors.toMap(KnowledgeBase::getId, kb -> kb, (a, b) -> a));

        List<Map<String, Object>> esResults = elasticsearchService.similaritySearchAcrossIndexes(indexes, vector, topK);
        return convertEsResultsToDto(esResults, null, indexMap, idMap);
    }

    private void ensureKnowledgeBaseIndex(KnowledgeBase kb) {
        if (kb == null) {
            return;
        }
        String esIndex = kb.getEsIndex();
        if (esIndex == null || esIndex.isBlank()) {
            return;
        }
        if (!elasticsearchService.indexExists(esIndex) || elasticsearchService.getIndexVectorDimension(esIndex) == null) {
            elasticsearchService.createIndex(esIndex);
            log.info("确保索引存在并带向量映射: {}", esIndex);
        }
    }

    private void ensureKnowledgeBasesIndexed(List<KnowledgeBase> knowledgeBases) {
        knowledgeBases.forEach(this::ensureKnowledgeBaseIndex);
    }

    private boolean shouldSearchAll(String kbId) {
        if (kbId == null) {
            return true;
        }
        String normalizedKbId = kbId.trim();
        return normalizedKbId.isBlank()
                || "null".equalsIgnoreCase(normalizedKbId)
                || "undefined".equalsIgnoreCase(normalizedKbId)
                || "all".equalsIgnoreCase(normalizedKbId);
    }

    private double normalizeScore(Double score, String searchType) {
        if (score == null) return 0.0;
        // 关键词搜索的ES分数通常很高，向量搜索的余弦相似度是0-1
        if ("vector".equals(searchType)) {
            return score * 100; // 转换为百分比
        }
        // 关键词分数取对数归一化
        return Math.log1p(score) / Math.log(100);
    }

    private List<KnowledgeSearchResultDTO> convertEsResultsToDto(
            List<Map<String, Object>> esResults, KnowledgeBase kb) {
        return convertEsResultsToDto(esResults, kb, null, null);
    }

    private List<KnowledgeSearchResultDTO> convertEsResultsToDto(
            List<Map<String, Object>> esResults,
            KnowledgeBase kb,
            Map<String, KnowledgeBase> indexMap,
            Map<String, KnowledgeBase> idMap) {

        return esResults.stream().map(result -> {
            KnowledgeBase resolvedKb = resolveKnowledgeBase(result, kb, indexMap, idMap);
            KnowledgeSearchResultDTO dto = new KnowledgeSearchResultDTO();
            dto.setId(getStringValue(result, "id"));
            dto.setTitle(getStringValue(result, "title"));
            dto.setContent(getStringValue(result, "content"));
            dto.setSummary(getStringValue(result, "summary"));
            if (resolvedKb != null) {
                dto.setKbId(resolvedKb.getId());
                dto.setKbName(resolvedKb.getName());
            } else {
                dto.setKbId(getStringValue(result, "kbId"));
                dto.setKbName(getStringValue(result, "kbName"));
            }
            dto.setSourceType(getStringValue(result, "sourceType"));
            dto.setSourceDocId(getStringValue(result, "sourceDocId"));
            dto.setOriginalFileName(getStringValue(result, "originalFileName"));
            dto.setFileType(getStringValue(result, "fileType"));
            dto.setScore(getDoubleValue(result, "score"));
            dto.setCreateTime(getStringValue(result, "createdAt"));

            if (dto.getSourceDocId() != null) {
                try {
                    Document doc = documentMapper.findById(dto.getSourceDocId()).orElse(null);
                    if (doc != null) {
                        dto.setOriginalFileName(doc.getOriginalName());
                        dto.setFileType(doc.getFileType());
                    }
                } catch (Exception e) {
                    log.debug("获取文档信息失败: {}", e.getMessage());
                }
            }

            return dto;
        }).collect(Collectors.toList());
    }

    private KnowledgeBase resolveKnowledgeBase(
            Map<String, Object> result,
            KnowledgeBase fallback,
            Map<String, KnowledgeBase> indexMap,
            Map<String, KnowledgeBase> idMap) {
        if (fallback != null) {
            return fallback;
        }
        if (indexMap != null) {
            String indexName = getStringValue(result, "_index");
            if (indexName != null) {
                KnowledgeBase kb = indexMap.get(indexName);
                if (kb != null) {
                    return kb;
                }
            }
        }
        if (idMap != null) {
            String kbId = getStringValue(result, "kbId");
            if (kbId != null) {
                return idMap.get(kbId);
            }
        }
        return null;
    }


    private String getStringValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    private Double getDoubleValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return 0.0;
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    /**
     * 解析标签
     */
    private void parseTags(KnowledgeSearchResultDTO dto) {
        if (dto.getTags() == null) {
            dto.setTags(Collections.emptyList());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public KnowledgeDetailDTO getDetail(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            throw new BusinessException("知识条目ID不能为空");
        }

        KnowledgeItem item = knowledgeItemMapper.findById(itemId).orElse(null);
        if (item == null) {
            throw new BusinessException("知识条目不存在");
        }

        KnowledgeBase kb = knowledgeBaseService.getById(item.getKbId());
        if (kb == null) {
            throw new BusinessException("知识库不存在");
        }

        KnowledgeDetailDTO dto = new KnowledgeDetailDTO();
        dto.setId(item.getId());
        dto.setKbId(kb.getId());
        dto.setKbName(kb.getName());
        dto.setTitle(item.getTitle());
        dto.setContent(item.getContent());
        dto.setSummary(item.getSummary());
        dto.setSourceType(item.getSourceType());
        dto.setOriginalFileName(item.getOriginalFileName());
        dto.setFileType(item.getFileType());
        dto.setMinioPath(item.getMinioPath());
        dto.setStatus(item.getStatus());
        dto.setCreateTime(item.getCreateTime() != null ? item.getCreateTime().toString() : null);
        dto.setUpdateTime(item.getUpdateTime() != null ? item.getUpdateTime().toString() : null);

        // 解析标签
        if (item.getTags() != null && !item.getTags().isBlank()) {
            try {
                dto.setTags(objectMapper.readValue(item.getTags(), new TypeReference<List<String>>() {}));
            } catch (Exception e) {
                dto.setTags(Collections.emptyList());
            }
        } else {
            dto.setTags(Collections.emptyList());
        }

        return dto;
    }

    @Override
    @Transactional(readOnly = true)
    public String getDocumentPreviewUrl(String docId) {
        Document doc = documentMapper.findById(docId).orElse(null);
        if (doc == null || doc.getMinioPath() == null) {
            throw new BusinessException("文档不存在");
        }

        return generatePresignedUrl(doc.getMinioPath(), 3600);
    }

    @Override
    @Transactional(readOnly = true)
    public String getDocumentDownloadUrl(String docId) {
        Document doc = documentMapper.findById(docId).orElse(null);
        if (doc == null || doc.getMinioPath() == null) {
            throw new BusinessException("文档不存在");
        }

        return generatePresignedUrl(doc.getMinioPath(), 3600);
    }

    /**
     * 生成预签名URL
     */
    private String generatePresignedUrl(String objectPath, int expirationSeconds) {
        // 简化实现，实际应使用MinIO SDK生成预签名URL
        return String.format("%s/%s/%s?token=generated", minioEndpoint, minioBucket, objectPath);
    }

    @Override
    @Transactional(readOnly = true)
    public List<KnowledgeBaseSimpleDTO> getAvailableKnowledgeBases() {
        return knowledgeBaseService.list().stream()
                .filter(kb -> kb.getStatus() != null && kb.getStatus() == 1)
                .map(kb -> {
                    KnowledgeBaseSimpleDTO dto = new KnowledgeBaseSimpleDTO();
                    dto.setId(kb.getId());
                    dto.setName(kb.getName());
                    dto.setCode(kb.getCode());
                    dto.setDescription(kb.getDescription());
                    dto.setItemCount(kb.getItemCount() != null ? kb.getItemCount() : 0);
                    return dto;
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> getSearchStatistics(Integer days) {
        int queryDays = days != null && days > 0 ? days : 7;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(queryDays - 1);

        String startDateStr = startDate.format(formatter);
        String endDateStr = endDate.format(formatter);

        // 从数据库获取日期范围内的统计数据
        var stats = invocationStatService.getStatsByDateRange(startDateStr, endDateStr);

        // 按日期分组汇总
        Map<String, Long> statMap = new HashMap<>();
        for (var stat : stats) {
            String date = stat.getStatDate();
            Long currentCount = statMap.getOrDefault(date, 0L);
            statMap.put(date, currentCount + (stat.getInvokeCount() != null ? stat.getInvokeCount() : 0L));
        }

        // 生成完整的日期范围内的统计数据（包含没有数据的日期）
        List<Map<String, Object>> statistics = new ArrayList<>();
        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            String dateStr = current.format(formatter);
            Map<String, Object> statItem = new HashMap<>();
            statItem.put("date", dateStr);
            statItem.put("count", statMap.getOrDefault(dateStr, 0L));
            statistics.add(statItem);
            current = current.plusDays(1);
        }
        return statistics;
    }
}

package com.aip.knowledge.service;

import com.aip.common.exception.BusinessException;
import com.aip.common.result.PageResult;
import com.aip.knowledge.config.ElasticsearchIndexConfig;
import com.aip.knowledge.dto.KnowledgeItemDTO;
import com.aip.knowledge.entity.KnowledgeBase;
import com.aip.knowledge.entity.KnowledgeItem;
import com.aip.knowledge.mapper.KnowledgeBaseMapper;
import com.aip.knowledge.mapper.KnowledgeItemMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aip.knowledge.service.chunk.ChunkType;
import com.aip.knowledge.service.chunk.StructuredContentChunker;
import com.aip.knowledge.service.chunk.TextChunk;

import java.util.*;

/**
 * 知识条目服务实现
 */
@Slf4j
@Service
public class KnowledgeItemServiceImpl implements IKnowledgeItemService {

    @Autowired
    private KnowledgeItemMapper knowledgeItemMapper;

    @Autowired
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Autowired
    private EmbeddingService embeddingService;

    @Autowired
    private ElasticsearchService elasticsearchService;

    @Autowired
    private ElasticsearchIndexConfig indexConfig;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StructuredContentChunker contentChunker;

    @Override
    public PageResult<KnowledgeItem> page(String kbId, String keyword, Integer status, int page, int size) {
        Page<KnowledgeItem> all = knowledgeItemMapper.findAll(PageRequest.of(page - 1, size));
        List<KnowledgeItem> filtered = all.getContent();

        if (kbId != null && !kbId.isBlank()) {
            filtered = filtered.stream()
                    .filter(item -> kbId.equals(item.getKbId()))
                    .toList();
        }

        if (keyword != null && !keyword.isBlank()) {
            filtered = filtered.stream()
                    .filter(item -> item.getTitle().contains(keyword) || item.getContent().contains(keyword))
                    .toList();
        }

        if (status != null) {
            filtered = filtered.stream()
                    .filter(item -> item.getStatus().equals(status))
                    .toList();
        }

        return PageResult.of(all.getTotalElements(), filtered, (long) page, (long) size);
    }

    @Override
    public List<KnowledgeItem> listByKbId(String kbId) {
        return knowledgeItemMapper.findByKbId(kbId);
    }

    @Override
    public KnowledgeItem getById(String id) {
        return knowledgeItemMapper.findById(id).orElse(null);
    }

    @Override
    @Transactional
    public KnowledgeItem create(KnowledgeItemDTO dto) {
        KnowledgeBase knowledgeBase = knowledgeBaseMapper.findById(dto.getKbId())
                .orElseThrow(() -> new BusinessException("知识库不存在"));

        KnowledgeItem item = new KnowledgeItem();
        BeanUtils.copyProperties(dto, item);
        item.setStatus(1);
        item.setVectorStatus(0);
        item.setSourceType(dto.getSourceType() != null ? dto.getSourceType() : "manual");
        if (dto.getMinioPath() != null) {
            item.setMinioPath(dto.getMinioPath());
            item.setOriginalFileName(dto.getOriginalFileName());
            item.setFileType(dto.getFileType());
        }

        if (dto.getTags() != null && !dto.getTags().isEmpty()) {
            try {
                item.setTags(objectMapper.writeValueAsString(dto.getTags()));
            } catch (JsonProcessingException e) {
                log.warn("标签序列化失败", e);
            }
        }

        item = knowledgeItemMapper.save(item);
        vectorizeItem(item, knowledgeBase);

        log.info("创建知识条目: {} -> KB:{}", item.getId(), dto.getKbId());
        return item;
    }

    @Override
    @Transactional
    public KnowledgeItem update(String id, KnowledgeItemDTO dto) {
        KnowledgeItem item = knowledgeItemMapper.findById(id)
                .orElseThrow(() -> new BusinessException("知识条目不存在"));

        KnowledgeBase knowledgeBase = knowledgeBaseMapper.findById(dto.getKbId())
                .orElseThrow(() -> new BusinessException("知识库不存在"));

        item.setKbId(dto.getKbId());
        item.setTitle(dto.getTitle());
        item.setContent(dto.getContent());
        item.setSummary(dto.getSummary());
        item.setStatus(dto.getStatus() != null ? dto.getStatus() : item.getStatus());
        item.setSourceType(dto.getSourceType() != null ? dto.getSourceType() : item.getSourceType());
        item.setSourceDocId(dto.getSourceDocId());
        item.setVectorStatus(0);

        if (dto.getMinioPath() != null && !dto.getMinioPath().isBlank()) {
            item.setMinioPath(dto.getMinioPath());
            item.setOriginalFileName(dto.getOriginalFileName());
            item.setFileType(dto.getFileType());
        } else {
            item.setMinioPath(null);
            item.setOriginalFileName(null);
            item.setFileType(null);
        }

        if (dto.getTags() != null) {
            try {
                item.setTags(objectMapper.writeValueAsString(dto.getTags()));
            } catch (JsonProcessingException e) {
                log.warn("标签序列化失败", e);
            }
        }

        item = knowledgeItemMapper.save(item);
        vectorizeItem(item, knowledgeBase);

        log.info("更新知识条目: {}", id);
        return item;
    }

    @Override
    @Transactional
    public void delete(String id) {
        KnowledgeItem item = knowledgeItemMapper.findById(id)
                .orElseThrow(() -> new BusinessException("知识条目不存在"));

        KnowledgeBase knowledgeBase = knowledgeBaseMapper.findById(item.getKbId())
                .orElse(null);

        if (knowledgeBase != null) {
            for (String index : resolveTargetIndexes(knowledgeBase)) {
                elasticsearchService.deleteDocumentsByItemId(index, id);
            }
        }

        knowledgeItemMapper.deleteById(id);
        log.info("删除知识条目: {}", id);
    }

    @Override
    @Transactional
    public void batchDelete(List<String> ids) {
        for (String id : ids) {
            delete(id);
        }
        log.info("批量删除知识条目: {} 条", ids.size());
    }

    @Override
    @Transactional
    public void updateStatus(String id, Integer status) {
        KnowledgeItem item = knowledgeItemMapper.findById(id)
                .orElseThrow(() -> new BusinessException("知识条目不存在"));
        item.setStatus(status);
        knowledgeItemMapper.save(item);
        log.info("修改知识条目状态: {} -> {}", id, status);
    }

    @Override
    @Transactional
    public void vectorizeItem(KnowledgeItem item, KnowledgeBase knowledgeBase) {
        try {
            item.setVectorStatus(1);
            knowledgeItemMapper.save(item);

            Set<String> targetIndexes = resolveTargetIndexes(knowledgeBase);
            targetIndexes.forEach(elasticsearchService::createIndex);

            String vectorIndex = resolveVectorIndex(knowledgeBase);
            int expectedDimension = resolveExpectedDimension(item, vectorIndex, knowledgeBase);

            for (String index : targetIndexes) {
                elasticsearchService.deleteDocumentsByItemId(index, item.getId());
            }

            List<TextChunk> chunks = contentChunker.chunk(item);
            if (chunks.isEmpty()) {
                String fallback = item.getContent() != null ? item.getContent() : "";
                chunks = List.of(new TextChunk(
                        fallback,
                        ChunkType.PARAGRAPH,
                        List.of(),
                        List.of(ChunkType.PARAGRAPH)
                ));
            }

            List<Map<String, Object>> documents = new ArrayList<>();

            for (int i = 0; i < chunks.size(); i++) {
                TextChunk chunk = chunks.get(i);
                if (chunk.text() == null || chunk.text().isBlank()) {
                    continue;
                }
                String chunkText = chunk.text();
                float[] vector = embeddingService.embed(chunkText);
                validateVectorDimension(vectorIndex, item, knowledgeBase, vector, expectedDimension);

                Map<String, Object> doc = new HashMap<>();
                doc.put("kbId", item.getKbId());
                doc.put("itemId", item.getId());
                doc.put("title", item.getTitle());
                doc.put("content", chunkText);
                doc.put("summary", item.getSummary());
                doc.put("tags", item.getTags());
                doc.put("status", item.getStatus());
                doc.put("chunkIndex", i);
                doc.put("totalChunks", chunks.size());
                doc.put("chunkType", chunk.primaryType().name());
                doc.put("chunkTypes", chunk.segmentTypes().stream().map(Enum::name).toList());
                doc.put("anchors", chunk.anchors());

                List<Float> vectorList = new ArrayList<>();
                for (float v : vector) {
                    vectorList.add(v);
                }
                doc.put("vector", vectorList);

                documents.add(doc);
            }

            if (!documents.isEmpty()) {
                for (String index : targetIndexes) {
                    elasticsearchService.bulkIndex(index, documents);
                }
            }

            item.setVectorStatus(2);
            item.setVectorChunks(chunks.size());
            knowledgeItemMapper.save(item);

            log.info("向量化完成: {} -> {} chunks", item.getId(), chunks.size());

        } catch (Exception e) {
            log.error("向量化失败: {}", item.getId(), e);
            item.setVectorStatus(3);
            knowledgeItemMapper.save(item);
            if (e instanceof BusinessException businessException) {
                throw businessException;
            }
            throw new BusinessException("向量化失败: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public void revectorize(String id) {
        KnowledgeItem item = knowledgeItemMapper.findById(id)
                .orElseThrow(() -> new BusinessException("知识条目不存在"));

        KnowledgeBase knowledgeBase = knowledgeBaseMapper.findById(item.getKbId())
                .orElseThrow(() -> new BusinessException("知识库不存在"));

        vectorizeItem(item, knowledgeBase);
    }

    @Override
    @Transactional
    public Map<String, Object> vectorizeAll(String kbId) {
        List<KnowledgeItem> items = listByKbId(kbId);
        KnowledgeBase knowledgeBase = knowledgeBaseMapper.findById(kbId)
                .orElseThrow(() -> new BusinessException("知识库不存在"));

        int success = 0;
        int failed = 0;

        for (KnowledgeItem item : items) {
            try {
                vectorizeItem(item, knowledgeBase);
                success++;
            } catch (Exception e) {
                log.error("向量化失败: {}", item.getId(), e);
                failed++;
            }
        }

        log.info("批量向量化完成: 成功={}, 失败={}", success, failed);

        Map<String, Object> result = new HashMap<>();
        result.put("kbId", kbId);
        result.put("total", items.size());
        result.put("success", success);
        result.put("failed", failed);
        return result;
    }

    @Override
    public Map<String, Object> getVectorizationStats(String kbId) {
        List<KnowledgeItem> items = listByKbId(kbId);

        long total = items.size();
        long pending = items.stream().filter(i -> i.getVectorStatus() == 0).count();
        long processing = items.stream().filter(i -> i.getVectorStatus() == 1).count();
        long completed = items.stream().filter(i -> i.getVectorStatus() == 2).count();
        long failed = items.stream().filter(i -> i.getVectorStatus() == 3).count();

        Map<String, Object> stats = new HashMap<>();
        stats.put("total", total);
        stats.put("pending", pending);
        stats.put("processing", processing);
        stats.put("completed", completed);
        stats.put("failed", failed);
        return stats;
    }

    @Override
    public List<String> parseTags(String tagsJson) {
        if (tagsJson == null || tagsJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(tagsJson, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            log.warn("标签解析失败: {}", tagsJson, e);
            return Collections.emptyList();
        }
    }

    private void validateVectorDimension(String indexName, KnowledgeItem item, KnowledgeBase knowledgeBase, float[] vector, int expectedDimension) {
        int actualDimension = vector == null ? 0 : vector.length;
        if (actualDimension != expectedDimension) {
            throw new BusinessException(String.format(
                    "向量维度不匹配: index=%s, itemId=%s, mapping=%d, embedding=%d。请调用 POST /kb/knowledge-base/%s/rebuild-index 重建索引，或调整 embedding 模型维度",
                    indexName,
                    item.getId(),
                    expectedDimension,
                    actualDimension,
                    knowledgeBase.getId()
            ));
        }
    }

    private int resolveExpectedDimension(KnowledgeItem item, String indexName, KnowledgeBase knowledgeBase) {
        Integer indexDimension = elasticsearchService.getIndexVectorDimension(indexName);
        if (indexDimension == null || indexDimension <= 0) {
            throw new BusinessException(String.format(
                    "知识库索引缺少向量映射: index=%s, itemId=%s。请调用 POST /kb/knowledge-base/%s/rebuild-index 重建索引",
                    indexName,
                    item.getId(),
                    knowledgeBase.getId()
            ));
        }

        int configuredDimension = indexConfig.getVectorDimension();
        if (indexDimension != configuredDimension) {
            throw new BusinessException(String.format(
                    "知识库索引维度与配置不一致: index=%s, mapping=%d, config=%d, itemId=%s。请调用 POST /kb/knowledge-base/%s/rebuild-index 重建索引",
                    indexName,
                    indexDimension,
                    configuredDimension,
                    item.getId(),
                    knowledgeBase.getId()
            ));
        }

        return indexDimension;
    }

    private Set<String> resolveTargetIndexes(KnowledgeBase knowledgeBase) {
        Set<String> indexes = new LinkedHashSet<>();
        if (knowledgeBase == null) {
            return indexes;
        }
        if (knowledgeBase.getEsIndex() != null) {
            indexes.add(knowledgeBase.getEsIndex());
        }
        String vectorIndex = resolveVectorIndex(knowledgeBase);
        if (vectorIndex != null) {
            indexes.add(vectorIndex);
        }
        return indexes;
    }

    private String resolveVectorIndex(KnowledgeBase knowledgeBase) {
        if (knowledgeBase == null) {
            return null;
        }
        String vectorIndex = knowledgeBase.getVectorIndex();
        if (vectorIndex != null && !vectorIndex.isBlank()) {
            return vectorIndex;
        }
        return knowledgeBase.getEsIndex();
    }
}

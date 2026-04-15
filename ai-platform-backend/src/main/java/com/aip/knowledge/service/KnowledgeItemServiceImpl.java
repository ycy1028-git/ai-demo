package com.aip.knowledge.service;

import com.aip.common.exception.BusinessException;
import com.aip.common.result.PageResult;
import com.aip.knowledge.config.ElasticsearchIndexConfig;
import com.aip.knowledge.config.MinioConfig;
import com.aip.knowledge.dto.KnowledgeItemDTO;
import com.aip.knowledge.entity.KnowledgeBase;
import com.aip.knowledge.entity.Document;
import com.aip.knowledge.entity.KnowledgeItem;
import com.aip.knowledge.mapper.DocumentMapper;
import com.aip.knowledge.mapper.KnowledgeBaseMapper;
import com.aip.knowledge.mapper.KnowledgeItemMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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
    private DocumentMapper documentMapper;

    @Autowired
    private MinioConfig minioConfig;

    @Autowired
    private MinioClient minioClient;

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

    private final Tika tika = new Tika();

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

        String sourceDocId = item.getSourceDocId();
        Document linkedDocument = null;
        String linkedBucketName = null;
        if (sourceDocId != null && !sourceDocId.isBlank()) {
            long refCount = knowledgeItemMapper.countActiveBySourceDocId(sourceDocId);
            if (refCount <= 1) {
                linkedDocument = documentMapper.findById(sourceDocId).orElse(null);
                if (linkedDocument != null) {
                    KnowledgeBase docKb = knowledgeBaseMapper.findById(linkedDocument.getKbId()).orElse(null);
                    linkedBucketName = resolveBucketName(docKb);
                }
            }
        }

        KnowledgeBase knowledgeBase = knowledgeBaseMapper.findById(item.getKbId())
                .orElse(null);

        if (knowledgeBase != null) {
            for (String index : resolveTargetIndexes(knowledgeBase)) {
                elasticsearchService.deleteDocumentsByItemId(index, id);
            }
        }

        knowledgeItemMapper.deleteById(id);

        if (linkedDocument != null && linkedBucketName != null) {
            removeMinioObject(linkedBucketName, linkedDocument.getMinioPath());
            removeMinioObject(linkedBucketName, buildPreviewPdfPath(linkedDocument.getMinioPath()));
            documentMapper.deleteById(linkedDocument.getId());
            log.info("删除知识时同步清理附件及预览: itemId={}, docId={}", id, linkedDocument.getId());
        }

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

            Document sourceDocument = resolveSourceDocument(item);
            List<TextChunk> fileChunks = resolveFileChunks(item, sourceDocument);
            int totalChunks = chunks.size() + fileChunks.size();
            List<Map<String, Object>> documents = new ArrayList<>();
            int chunkIndex = 0;

            for (TextChunk chunk : chunks) {
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
                doc.put("sourceType", item.getSourceType());
                doc.put("sourceDocId", item.getSourceDocId());
                doc.put("originalFileName", item.getOriginalFileName());
                doc.put("fileType", item.getFileType());
                doc.put("chunkIndex", chunkIndex++);
                doc.put("totalChunks", totalChunks);
                doc.put("chunkSource", "BODY");
                doc.put("chunkType", chunk.primaryType().name());
                doc.put("chunkTypes", chunk.segmentTypes().stream().map(Enum::name).toList());
                doc.put("anchors", chunk.anchors());
                if (sourceDocument != null) {
                    doc.put("file", buildFileObject(sourceDocument, null));
                }

                List<Float> vectorList = new ArrayList<>();
                for (float v : vector) {
                    vectorList.add(v);
                }
                doc.put("vector", vectorList);

                documents.add(doc);
            }

            for (TextChunk chunk : fileChunks) {
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
                doc.put("content", null);
                doc.put("summary", item.getSummary());
                doc.put("tags", item.getTags());
                doc.put("status", item.getStatus());
                doc.put("sourceType", item.getSourceType());
                doc.put("sourceDocId", item.getSourceDocId());
                doc.put("originalFileName", item.getOriginalFileName());
                doc.put("fileType", item.getFileType());
                doc.put("chunkIndex", chunkIndex++);
                doc.put("totalChunks", totalChunks);
                doc.put("chunkSource", "FILE");
                doc.put("chunkType", chunk.primaryType().name());
                doc.put("chunkTypes", chunk.segmentTypes().stream().map(Enum::name).toList());
                doc.put("anchors", chunk.anchors());
                doc.put("file", buildFileObject(sourceDocument, chunkText));

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
            item.setVectorChunks(documents.size());
            knowledgeItemMapper.save(item);

            log.info("向量化完成: {} -> body={}, file={}, indexed={}", item.getId(), chunks.size(), fileChunks.size(), documents.size());

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

    private Document resolveSourceDocument(KnowledgeItem item) {
        if (item == null || item.getSourceDocId() == null || item.getSourceDocId().isBlank()) {
            return null;
        }
        return documentMapper.findById(item.getSourceDocId()).orElse(null);
    }

    private List<TextChunk> resolveFileChunks(KnowledgeItem item, Document sourceDocument) {
        if (sourceDocument == null) {
            return Collections.emptyList();
        }

        String extractedText = sourceDocument.getExtractText();
        if (extractedText == null || extractedText.isBlank()) {
            try {
                extractedText = extractDocumentTextFromStorage(item, sourceDocument);
                sourceDocument = documentMapper.findById(sourceDocument.getId()).orElse(sourceDocument);
            } catch (Exception e) {
                throw new BusinessException(String.format(
                        "附件文本抽取失败，无法建立 file.content 索引: itemId=%s, docId=%s",
                        item.getId(),
                        sourceDocument.getId()
                ), e);
            }
        }

        if (extractedText == null || extractedText.isBlank()) {
            throw new BusinessException(String.format(
                    "附件文本为空，无法建立 file.content 索引: itemId=%s, docId=%s",
                    item.getId(),
                    sourceDocument.getId()
            ));
        }

        KnowledgeItem fileItem = new KnowledgeItem();
        fileItem.setTitle(sourceDocument.getName());
        fileItem.setContent(extractedText);
        fileItem.setOriginalFileName(sourceDocument.getOriginalName());
        fileItem.setFileType(sourceDocument.getFileType());

        return contentChunker.chunk(fileItem);
    }

    private Map<String, Object> buildFileObject(Document sourceDocument, String fileContent) {
        if (sourceDocument == null) {
            return null;
        }
        Map<String, Object> file = new HashMap<>();
        file.put("docId", sourceDocument.getId());
        file.put("name", sourceDocument.getOriginalName());
        file.put("type", sourceDocument.getFileType());
        if (fileContent != null && !fileContent.isBlank()) {
            file.put("content", fileContent);
        }
        return file;
    }

    private String extractDocumentTextFromStorage(KnowledgeItem item, Document sourceDocument) throws Exception {
        if (sourceDocument == null || !StringUtils.hasText(sourceDocument.getMinioPath())) {
            return null;
        }

        KnowledgeBase knowledgeBase = knowledgeBaseMapper.findById(item.getKbId()).orElse(null);
        String bucketName = resolveBucketName(knowledgeBase);

        sourceDocument.setExtractStatus(1);
        documentMapper.save(sourceDocument);

        try (GetObjectResponse response = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucketName)
                        .object(sourceDocument.getMinioPath())
                        .build())) {
            String text = tika.parseToString(response);
            sourceDocument.setExtractText(text);
            sourceDocument.setExtractStatus(2);
            sourceDocument.setErrorMsg(null);
            documentMapper.save(sourceDocument);
            return text;
        } catch (Exception e) {
            sourceDocument.setExtractStatus(3);
            sourceDocument.setErrorMsg(e.getMessage());
            documentMapper.save(sourceDocument);
            throw e;
        }
    }

    private void removeMinioObject(String bucketName, String objectPath) {
        if (!StringUtils.hasText(bucketName) || !StringUtils.hasText(objectPath)) {
            return;
        }
        try {
            minioClient.removeObject(
                    io.minio.RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectPath)
                            .build()
            );
        } catch (Exception e) {
            log.warn("删除MinIO对象失败: bucket={}, object={}", bucketName, objectPath, e);
        }
    }

    private String buildPreviewPdfPath(String minioPath) {
        if (!StringUtils.hasText(minioPath)) {
            return null;
        }
        int slashIndex = minioPath.lastIndexOf('/');
        String dir = slashIndex >= 0 ? minioPath.substring(0, slashIndex + 1) : "";
        String fileName = slashIndex >= 0 ? minioPath.substring(slashIndex + 1) : minioPath;
        int dotIndex = fileName.lastIndexOf('.');
        String baseName = dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
        return dir + "_preview/" + baseName + ".pdf";
    }

    private String resolveBucketName(KnowledgeBase knowledgeBase) {
        if (knowledgeBase == null) {
            return minioConfig.getBucketName();
        }
        return StringUtils.hasText(knowledgeBase.getBucketName())
                ? knowledgeBase.getBucketName()
                : minioConfig.getBucketName();
    }
}

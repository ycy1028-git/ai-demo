package com.aip.knowledge.service;

import com.aip.common.exception.BusinessException;
import com.aip.common.result.PageResult;
import com.aip.knowledge.config.ElasticsearchIndexConfig;
import com.aip.knowledge.config.MinioConfig;
import com.aip.knowledge.dto.KnowledgeBaseDTO;
import com.aip.knowledge.entity.KnowledgeBase;
import com.aip.knowledge.mapper.KnowledgeBaseMapper;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 知识库服务实现
 */
@Slf4j
@Service
public class KnowledgeBaseServiceImpl implements IKnowledgeBaseService {

    @Autowired
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Autowired
    private ElasticsearchService elasticsearchService;

    @Autowired
    private ElasticsearchIndexConfig indexConfig;

    @Autowired
    private MinioClient minioClient;

    @Autowired
    private MinioConfig minioConfig;

    @Override
    public PageResult<KnowledgeBase> page(String keyword, String type, Integer status, int page, int size) {
        Page<KnowledgeBase> all = knowledgeBaseMapper.findAll(PageRequest.of(page - 1, size));
        List<KnowledgeBase> filtered = all.getContent();

        if (keyword != null && !keyword.isBlank()) {
            filtered = filtered.stream()
                    .filter(kb -> kb.getName().contains(keyword) || kb.getCode().contains(keyword))
                    .toList();
        }

        if (status != null) {
            filtered = filtered.stream()
                    .filter(kb -> kb.getStatus().equals(status))
                    .toList();
        }

        return PageResult.of(all.getTotalElements(), filtered, (long) page, (long) size);
    }

    @Override
    public List<KnowledgeBase> list() {
        return knowledgeBaseMapper.findAll();
    }

    @Override
    public KnowledgeBase getById(String id) {
        return knowledgeBaseMapper.findById(id).orElse(null);
    }

    @Override
    public KnowledgeBase getByCode(String code) {
        return knowledgeBaseMapper.findByCode(code).orElse(null);
    }

    @Override
    public String getEsIndexByCode(String code) {
        KnowledgeBase kb = getByCode(code);
        return kb != null ? kb.getEsIndex() : null;
    }

    @Override
    public String getVectorIndexByCode(String code) {
        KnowledgeBase kb = getByCode(code);
        return kb != null ? resolveVectorIndex(kb) : null;
    }

    @Override
    @Transactional
    public KnowledgeBase create(KnowledgeBaseDTO dto) {
        if (knowledgeBaseMapper.existsByCode(dto.getCode())) {
            throw new BusinessException("知识库编码已存在");
        }

        String esIndex = determineIndexName(dto.getEsIndex(), indexConfig.generateIndexName(dto.getCode()));
        String vectorIndexCandidate = determineIndexName(dto.getVectorIndex(), null);
        String vectorIndex = StringUtils.hasText(vectorIndexCandidate) ? vectorIndexCandidate : esIndex;

        ensureIndexNotInUse(esIndex, null);
        ensureIndexNotInUse(vectorIndex, null);

        Set<String> indexes = new LinkedHashSet<>();
        indexes.add(esIndex);
        indexes.add(vectorIndex);
        indexes.forEach(elasticsearchService::createIndex);

        String bucketName = resolveBucketNameForCreate(dto.getBucketName());

        KnowledgeBase knowledgeBase = new KnowledgeBase();
        BeanUtils.copyProperties(dto, knowledgeBase);
        knowledgeBase.setEsIndex(esIndex);
        knowledgeBase.setVectorIndex(vectorIndex);
        knowledgeBase.setBucketName(bucketName);
        knowledgeBase.setStatus(dto.getStatus() != null ? dto.getStatus() : 1);

        log.info("创建知识库: {} -> textIndex={}, vectorIndex={}, bucket={}",
                dto.getName(), esIndex, vectorIndex, bucketName);
        return knowledgeBaseMapper.save(knowledgeBase);
    }

    @Override
    public List<KnowledgeBase> matchByQuestion(String userQuestion) {
        if (userQuestion == null || userQuestion.isBlank()) {
            return List.of();
        }

        return knowledgeBaseMapper.findAll().stream()
                .filter(kb -> kb.getStatus() != null && kb.getStatus() == 1)
                .sorted((a, b) -> Integer.compare(
                        b.getPriority() != null ? b.getPriority() : 0,
                        a.getPriority() != null ? a.getPriority() : 0
                ))
                .toList();
    }

    @Override
    @Transactional
    public KnowledgeBase update(String id, KnowledgeBaseDTO dto) {
        KnowledgeBase knowledgeBase = knowledgeBaseMapper.findById(id)
                .orElseThrow(() -> new BusinessException("知识库不存在"));

        if (!knowledgeBase.getCode().equals(dto.getCode()) && knowledgeBaseMapper.existsByCode(dto.getCode())) {
            throw new BusinessException("知识库编码已存在");
        }

        knowledgeBase.setName(dto.getName());
        knowledgeBase.setCode(dto.getCode());
        knowledgeBase.setDescription(dto.getDescription());
        knowledgeBase.setStatus(dto.getStatus() != null ? dto.getStatus() : knowledgeBase.getStatus());
        knowledgeBase.setSceneDescription(dto.getSceneDescription());
        knowledgeBase.setModelConfigId(dto.getModelConfigId());
        knowledgeBase.setPriority(dto.getPriority() != null ? dto.getPriority() : 0);

        String oldEsIndex = knowledgeBase.getEsIndex();
        String requestedEsIndex = determineIndexName(dto.getEsIndex(), null);
        String targetEsIndex = StringUtils.hasText(requestedEsIndex) ? requestedEsIndex : oldEsIndex;

        String requestedVectorIndex = determineIndexName(dto.getVectorIndex(), null);
        String targetVectorIndex = StringUtils.hasText(requestedVectorIndex) ? requestedVectorIndex : targetEsIndex;
        String effectiveOldVectorIndex = resolveVectorIndex(knowledgeBase);

        ensureIndexNotInUse(targetEsIndex, knowledgeBase.getId());
        ensureIndexNotInUse(targetVectorIndex, knowledgeBase.getId());

        if (!Objects.equals(targetEsIndex, oldEsIndex)) {
            elasticsearchService.deleteIndex(oldEsIndex);
            elasticsearchService.createIndex(targetEsIndex);
            knowledgeBase.setEsIndex(targetEsIndex);
        }

        if (!Objects.equals(targetVectorIndex, effectiveOldVectorIndex)) {
            String existingVectorIndex = knowledgeBase.getVectorIndex();
            if (StringUtils.hasText(existingVectorIndex)
                    && !existingVectorIndex.equals(oldEsIndex)
                    && !existingVectorIndex.equals(targetEsIndex)
                    && !existingVectorIndex.equals(targetVectorIndex)) {
                elasticsearchService.deleteIndex(existingVectorIndex);
            }
            if (!Objects.equals(targetVectorIndex, targetEsIndex)) {
                elasticsearchService.createIndex(targetVectorIndex);
            }
            knowledgeBase.setVectorIndex(targetVectorIndex);
        } else if (!Objects.equals(knowledgeBase.getVectorIndex(), targetVectorIndex)) {
            knowledgeBase.setVectorIndex(targetVectorIndex);
        }

        String bucketName = resolveBucketNameForUpdate(dto.getBucketName(), knowledgeBase.getBucketName());
        knowledgeBase.setBucketName(bucketName);

        log.info("更新知识库: {} -> textIndex={}, vectorIndex={}, bucket={}", id, targetEsIndex, targetVectorIndex, bucketName);
        return knowledgeBaseMapper.save(knowledgeBase);
    }

    @Override
    @Transactional
    public void delete(String id) {
        KnowledgeBase knowledgeBase = knowledgeBaseMapper.findById(id)
                .orElseThrow(() -> new BusinessException("知识库不存在"));

        elasticsearchService.deleteIndex(knowledgeBase.getEsIndex());
        String vectorIndex = resolveVectorIndex(knowledgeBase);
        if (!Objects.equals(vectorIndex, knowledgeBase.getEsIndex())) {
            elasticsearchService.deleteIndex(vectorIndex);
        }

        knowledgeBaseMapper.deleteById(id);

        log.info("删除知识库: {} -> textIndex={}, vectorIndex={}", id, knowledgeBase.getEsIndex(), vectorIndex);
    }

    @Override
    @Transactional
    public void updateStatus(String id, Integer status) {
        KnowledgeBase knowledgeBase = knowledgeBaseMapper.findById(id)
                .orElseThrow(() -> new BusinessException("知识库不存在"));
        knowledgeBase.setStatus(status);
        knowledgeBaseMapper.save(knowledgeBase);
        log.info("修改知识库状态: {} -> {}", id, status);
    }

    @Override
    @Transactional
    public void rebuildIndex(String id) {
        KnowledgeBase knowledgeBase = knowledgeBaseMapper.findById(id)
                .orElseThrow(() -> new BusinessException("知识库不存在"));

        elasticsearchService.deleteIndex(knowledgeBase.getEsIndex());
        elasticsearchService.createIndex(knowledgeBase.getEsIndex());

        String vectorIndex = resolveVectorIndex(knowledgeBase);
        if (!Objects.equals(vectorIndex, knowledgeBase.getEsIndex())) {
            elasticsearchService.deleteIndex(vectorIndex);
            elasticsearchService.createIndex(vectorIndex);
        }

        log.info("重建知识库索引: {} -> textIndex={}, vectorIndex={}", id, knowledgeBase.getEsIndex(), vectorIndex);
    }

    @Override
    public Map<String, Object> getStatistics() {
        List<KnowledgeBase> all = knowledgeBaseMapper.findAll();
        long total = all.size();
        long enabled = all.stream().filter(kb -> kb.getStatus() == 1).count();
        long disabled = all.stream().filter(kb -> kb.getStatus() == 0).count();

        Map<String, Object> stats = new HashMap<>();
        stats.put("total", total);
        stats.put("enabled", enabled);
        stats.put("disabled", disabled);
        return stats;
    }

    private String determineIndexName(String preferred, String fallback) {
        String normalizedPreferred = normalizeIndexName(preferred);
        if (StringUtils.hasText(normalizedPreferred)) {
            return normalizedPreferred;
        }
        return normalizeIndexName(fallback);
    }

    private String normalizeIndexName(String indexName) {
        if (!StringUtils.hasText(indexName)) {
            return null;
        }
        return indexName.trim().toLowerCase(Locale.ROOT);
    }

    private void ensureIndexNotInUse(String indexName, String excludeId) {
        if (!StringUtils.hasText(indexName)) {
            return;
        }

        knowledgeBaseMapper.findByEsIndex(indexName)
                .filter(existing -> !Objects.equals(existing.getId(), excludeId))
                .ifPresent(existing -> {
                    throw new BusinessException("索引名称已被知识库占用: " + indexName);
                });

        knowledgeBaseMapper.findByVectorIndex(indexName)
                .filter(existing -> !Objects.equals(existing.getId(), excludeId))
                .ifPresent(existing -> {
                    throw new BusinessException("索引名称已被知识库占用: " + indexName);
                });
    }

    private String resolveVectorIndex(KnowledgeBase knowledgeBase) {
        if (knowledgeBase == null) {
            return null;
        }
        return StringUtils.hasText(knowledgeBase.getVectorIndex())
                ? knowledgeBase.getVectorIndex()
                : knowledgeBase.getEsIndex();
    }

    private String resolveBucketNameForCreate(String requested) {
        String normalized = normalizeBucketName(requested);
        String bucket = StringUtils.hasText(normalized) ? normalized : minioConfig.getBucketName();
        ensureBucketExists(bucket);
        return bucket;
    }

    private String resolveBucketNameForUpdate(String requested, String current) {
        if (!StringUtils.hasText(requested)) {
            if (StringUtils.hasText(current)) {
                return current;
            }
            return resolveBucketNameForCreate(null);
        }
        String normalized = normalizeBucketName(requested);
        ensureBucketExists(normalized);
        return normalized;
    }

    private String normalizeBucketName(String bucketName) {
        if (!StringUtils.hasText(bucketName)) {
            return null;
        }
        return bucketName.trim().toLowerCase(Locale.ROOT);
    }

    private void ensureBucketExists(String bucketName) {
        if (!StringUtils.hasText(bucketName)) {
            throw new BusinessException("MinIO 桶名称不能为空");
        }
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucketName).build()
            );
            if (!exists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder().bucket(bucketName).build()
                );
                log.info("创建 MinIO 存储桶: {}", bucketName);
            }
        } catch (Exception e) {
            throw new BusinessException("创建MinIO存储桶失败: " + e.getMessage(), e);
        }
    }
}

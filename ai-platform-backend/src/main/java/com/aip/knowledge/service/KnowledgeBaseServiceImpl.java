package com.aip.knowledge.service;

import com.aip.common.exception.BusinessException;
import com.aip.common.result.PageResult;
import com.aip.knowledge.config.ElasticsearchIndexConfig;
import com.aip.knowledge.dto.KnowledgeBaseDTO;
import com.aip.knowledge.entity.KnowledgeBase;
import com.aip.knowledge.mapper.KnowledgeBaseMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    @Transactional
    public KnowledgeBase create(KnowledgeBaseDTO dto) {
        if (knowledgeBaseMapper.existsByCode(dto.getCode())) {
            throw new BusinessException("知识库编码已存在");
        }

        String esIndex = dto.getEsIndex();
        if (esIndex == null || esIndex.isBlank()) {
            esIndex = indexConfig.generateIndexName(dto.getCode());
        }

        elasticsearchService.createIndex(esIndex);

        KnowledgeBase knowledgeBase = new KnowledgeBase();
        BeanUtils.copyProperties(dto, knowledgeBase);
        knowledgeBase.setEsIndex(esIndex);
        knowledgeBase.setStatus(1);

        log.info("创建知识库: {} -> {}", dto.getName(), esIndex);
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

        String newEsIndex = dto.getEsIndex();
        if (newEsIndex != null && !newEsIndex.equals(knowledgeBase.getEsIndex())) {
            elasticsearchService.deleteIndex(knowledgeBase.getEsIndex());
            elasticsearchService.createIndex(newEsIndex);
            knowledgeBase.setEsIndex(newEsIndex);
        }

        log.info("更新知识库: {}", id);
        return knowledgeBaseMapper.save(knowledgeBase);
    }

    @Override
    @Transactional
    public void delete(String id) {
        KnowledgeBase knowledgeBase = knowledgeBaseMapper.findById(id)
                .orElseThrow(() -> new BusinessException("知识库不存在"));

        elasticsearchService.deleteIndex(knowledgeBase.getEsIndex());

        knowledgeBaseMapper.deleteById(id);

        log.info("删除知识库: {} -> {}", id, knowledgeBase.getEsIndex());
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

        log.info("重建知识库索引: {} -> {}", id, knowledgeBase.getEsIndex());
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
}

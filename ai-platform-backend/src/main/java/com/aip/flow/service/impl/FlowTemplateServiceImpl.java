package com.aip.flow.service.impl;

import com.aip.common.exception.BusinessException;
import com.aip.flow.entity.FlowTemplate;
import com.aip.flow.mapper.FlowTemplateMapper;
import com.aip.flow.service.IFlowTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 流程模板服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowTemplateServiceImpl implements IFlowTemplateService {

    private final FlowTemplateMapper flowTemplateMapper;

    @Override
    public Page<FlowTemplate> findPage(String keyword, Integer status, Pageable pageable) {
        List<FlowTemplate> list = flowTemplateMapper.findAll(pageable).getContent();

        if (keyword != null && !keyword.isBlank()) {
            String lowerKeyword = keyword.toLowerCase();
            list = list.stream()
                    .filter(template ->
                        template.getTemplateCode().toLowerCase().contains(lowerKeyword) ||
                        template.getTemplateName().toLowerCase().contains(lowerKeyword) ||
                        (template.getDescription() != null && template.getDescription().toLowerCase().contains(lowerKeyword)))
                    .collect(Collectors.toList());
        }

        if (status != null) {
            list = list.stream()
                    .filter(template -> template.getStatus().equals(status))
                    .collect(Collectors.toList());
        }

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), list.size());
        List<FlowTemplate> pageContent = start < list.size() ? list.subList(start, end) : List.of();

        return new PageImpl<>(pageContent, pageable, list.size());
    }

    @Override
    public List<FlowTemplate> findAllEnabled() {
        return flowTemplateMapper.findAllEnabledOrderByPriority();
    }

    @Override
    public Optional<FlowTemplate> findById(String id) {
        return flowTemplateMapper.findById(id)
                .filter(t -> !t.getDeleted());
    }

    @Override
    public Optional<FlowTemplate> findByCode(String templateCode) {
        return flowTemplateMapper.findByTemplateCodeAndDeletedFalse(templateCode);
    }

    @Override
    @Transactional
    public FlowTemplate create(FlowTemplate template) {
        template.setStatus(0);
        template.setPriority(0);
        template.setIsFallback(0);
        return flowTemplateMapper.save(template);
    }

    @Override
    @Transactional
    public FlowTemplate update(FlowTemplate template) {
        if (template.getId() == null) {
            throw new BusinessException("模板ID不能为空");
        }
        return flowTemplateMapper.save(template);
    }

    @Override
    @Transactional
    public void delete(String id) {
        flowTemplateMapper.findById(id).ifPresent(template -> {
            template.markDeleted();
            flowTemplateMapper.save(template);
        });
    }

    @Override
    @Transactional
    public void updateStatus(String id, Integer status) {
        FlowTemplate template = flowTemplateMapper.findById(id)
                .orElseThrow(() -> new BusinessException("模板不存在"));
        template.setStatus(status);
        flowTemplateMapper.save(template);
    }

    @Override
    @Transactional
    public void publish(String id) {
        FlowTemplate template = flowTemplateMapper.findById(id)
                .orElseThrow(() -> new BusinessException("模板不存在"));
        template.setStatus(1);
        template.setPublishedAt(Instant.now());
        flowTemplateMapper.save(template);
    }

    @Override
    public boolean existsByCode(String code) {
        return flowTemplateMapper.findByTemplateCodeAndDeletedFalse(code).isPresent();
    }

    @Override
    public boolean existsByCodeAndNotId(String code, String id) {
        return flowTemplateMapper.existsByTemplateCodeAndNotId(code, id);
    }
}

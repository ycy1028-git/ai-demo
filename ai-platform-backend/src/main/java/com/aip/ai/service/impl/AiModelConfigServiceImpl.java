package com.aip.ai.service.impl;

import com.aip.ai.dto.AiModelConfigDTO;
import com.aip.ai.dto.AiModelConfigVO;
import com.aip.ai.entity.AiModelConfig;
import com.aip.ai.mapper.AiModelConfigMapper;
import com.aip.ai.service.IAiModelConfigService;
import com.aip.common.ai.UnifiedLlmService;
import com.aip.common.exception.BusinessException;
import com.aip.common.exception.ErrorCode;
import com.aip.common.result.PageResult;
import com.aip.common.result.Result;
import com.aip.common.utils.ApiKeyEncryptUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * AI大模型配置服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiModelConfigServiceImpl implements IAiModelConfigService {

    private final AiModelConfigMapper aiModelConfigMapper;
    private final UnifiedLlmService unifiedLlmService;

    @Override
    public List<AiModelConfigVO> listAll() {
        return aiModelConfigMapper.findAll().stream()
                .filter(c -> !c.getDeleted())
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    public PageResult<AiModelConfigVO> listPage(String name, String provider, Boolean enabled, int page, int size) {
        Page<AiModelConfig> pageResult = aiModelConfigMapper.findAll(PageRequest.of(page - 1, size));

        log.info("JPA分页返回: totalElements={}, content={}", pageResult.getTotalElements(), pageResult.getContent().size());

        // 后端过滤（因为 JPA 查询限制）
        List<AiModelConfig> filtered = pageResult.getContent().stream()
                .filter(c -> !c.getDeleted())
                .filter(c -> name == null || name.isBlank() || c.getName().contains(name))
                .filter(c -> provider == null || provider.isBlank() || c.getProvider().equals(provider))
                .filter(c -> enabled == null || c.getEnabled().equals(enabled))
                .collect(Collectors.toList());

        log.info("过滤后记录数: {}, 返回PageResult", filtered.size());

        return PageResult.of(pageResult.getTotalElements(), filtered.stream().map(this::toVO).collect(Collectors.toList()), (long) page, (long) size);
    }

    @Override
    public List<AiModelConfigVO> listEnabled() {
        return aiModelConfigMapper.findAllEnabled().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    public AiModelConfigVO getById(String id) {
        return aiModelConfigMapper.findAll().stream()
                .filter(c -> id.equals(c.getId()))
                .filter(c -> !c.getDeleted())
                .map(this::toVO)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "模型配置不存在"));
    }

    @Override
    @Transactional
    public Result<AiModelConfigVO> create(AiModelConfigDTO dto) {
        // 检查名称是否重复
        if (aiModelConfigMapper.existsByName(dto.getName())) {
            return Result.fail(ErrorCode.PARAM_INVALID.getCode(), "模型名称已存在");
        }

        AiModelConfig config = toEntity(dto);

        // 如果设置为默认，先清除其他默认
        if (Boolean.TRUE.equals(dto.getIsDefault())) {
            aiModelConfigMapper.clearDefaultFlags();
        }

        AiModelConfig saved = aiModelConfigMapper.save(config);
        log.info("创建AI模型配置成功: {}", saved.getName());

        return Result.ok(toVO(saved));
    }

    @Override
    @Transactional
    public Result<AiModelConfigVO> update(String id, AiModelConfigDTO dto) {
        AiModelConfig existing = aiModelConfigMapper.findAll().stream()
                .filter(c -> id.equals(c.getId()))
                .filter(c -> !c.getDeleted())
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "模型配置不存在"));

        // 检查名称是否重复（排除自己）
        Optional<AiModelConfig> sameName = aiModelConfigMapper.findByName(dto.getName());
        if (sameName.isPresent() && !sameName.get().getId().equals(id)) {
            return Result.fail(ErrorCode.PARAM_INVALID.getCode(), "模型名称已存在");
        }

        // 如果设置为默认，先清除其他默认
        if (Boolean.TRUE.equals(dto.getIsDefault()) && !Boolean.TRUE.equals(existing.getIsDefault())) {
            aiModelConfigMapper.clearDefaultFlags();
        }

        // 更新字段
        updateEntity(existing, dto);
        AiModelConfig saved = aiModelConfigMapper.save(existing);
        log.info("更新AI模型配置成功: {}", saved.getName());

        return Result.ok(toVO(saved));
    }

    @Override
    @Transactional
    public Result<Void> delete(String id) {
        AiModelConfig config = aiModelConfigMapper.findAll().stream()
                .filter(c -> id.equals(c.getId()))
                .filter(c -> !c.getDeleted())
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "模型配置不存在"));

        // 软删除
        config.setDeleted(true);
        config.setIsDefault(false);
        aiModelConfigMapper.save(config);
        log.info("删除AI模型配置: {}", config.getName());

        return Result.ok();
    }

    @Override
    public AiModelConfig getDefaultModel() {
        return aiModelConfigMapper.findDefault()
                .orElseThrow(() -> new BusinessException(ErrorCode.SYSTEM_ERROR, "未配置默认AI模型"));
    }

    @Override
    public AiModelConfig getModelById(String id) {
        return aiModelConfigMapper.findAll().stream()
                .filter(c -> id.equals(c.getId()))
                .filter(c -> !c.getDeleted())
                .filter(c -> Boolean.TRUE.equals(c.getEnabled()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "AI模型不存在或未启用"));
    }

    @Override
    @Transactional
    public Result<Void> setDefault(String id) {
        AiModelConfig config = aiModelConfigMapper.findAll().stream()
                .filter(c -> id.equals(c.getId()))
                .filter(c -> !c.getDeleted())
                .filter(c -> Boolean.TRUE.equals(c.getEnabled()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "模型配置不存在或未启用"));

        aiModelConfigMapper.clearDefaultFlags();
        aiModelConfigMapper.setAsDefault(id);
        log.info("设置默认AI模型: {}", config.getName());

        return Result.ok();
    }

    @Override
    public Result<String> testConnection(String id) {
        AiModelConfig config = getModelById(id);
        try {
            // 使用统一LLM服务测试连接
            String result = unifiedLlmService.testConnection(config);
            log.info("模型 {} 连接测试成功", config.getName());
            return Result.ok("连接测试成功：" + result);
        } catch (Exception e) {
            log.error("模型 {} 连接测试失败: {}", config.getName(), e.getMessage());
            return Result.fail("连接测试失败：" + e.getMessage());
        }
    }

    @Override
    public Result<String> getAllModelIds() {
        List<AiModelConfig> all = aiModelConfigMapper.findAll();
        StringBuilder sb = new StringBuilder("数据库中所有模型ID:\n");
        for (AiModelConfig c : all) {
            sb.append("ID: ").append(c.getId())
              .append(", Name: ").append(c.getName())
              .append(", Deleted: ").append(c.getDeleted())
              .append(", enabled: ").append(c.getEnabled())
              .append("\n");
        }
        return Result.ok(sb.toString());
    }

    /**
     * DTO转实体
     */
    private AiModelConfig toEntity(AiModelConfigDTO dto) {
        AiModelConfig config = new AiModelConfig();
        config.setName(dto.getName());
        config.setProvider(dto.getProvider());
        config.setApiUrl(dto.getApiUrl());
        // API Key 加密存储
        config.setApiKey(ApiKeyEncryptUtil.encrypt(dto.getApiKey()));
        config.setModelName(dto.getModelName());
        config.setEmbeddingApiUrl(dto.getEmbeddingApiUrl());
        config.setEmbeddingModelName(dto.getEmbeddingModelName());
        config.setTemperature(dto.getTemperature());
        config.setMaxTokens(dto.getMaxTokens());
        // enabled 与 status 同步
        if (Boolean.TRUE.equals(dto.getEnabled())) {
            config.enable();
        } else {
            config.disable();
        }
        config.setIsDefault(dto.getIsDefault());
        config.setSortOrder(dto.getSortOrder());
        config.setDescription(dto.getDescription());
        return config;
    }

    /**
     * 更新实体
     */
    private void updateEntity(AiModelConfig existing, AiModelConfigDTO dto) {
        existing.setName(dto.getName());
        existing.setProvider(dto.getProvider());
        existing.setApiUrl(dto.getApiUrl());
        // API Key 加密存储，只在有新密钥时才更新
        if (dto.getApiKey() != null && !dto.getApiKey().isEmpty()) {
            existing.setApiKey(ApiKeyEncryptUtil.encrypt(dto.getApiKey()));
        }
        existing.setModelName(dto.getModelName());
        existing.setEmbeddingApiUrl(dto.getEmbeddingApiUrl());
        existing.setEmbeddingModelName(dto.getEmbeddingModelName());
        existing.setTemperature(dto.getTemperature());
        existing.setMaxTokens(dto.getMaxTokens());
        // enabled 与 status 同步
        if (dto.getEnabled() != null) {
            if (dto.getEnabled()) {
                existing.enable();
            } else {
                existing.disable();
            }
        }
        existing.setIsDefault(dto.getIsDefault());
        existing.setSortOrder(dto.getSortOrder());
        existing.setDescription(dto.getDescription());
    }

    /**
     * 实体转VO
     */
    private AiModelConfigVO toVO(AiModelConfig config) {
        AiModelConfigVO vo = new AiModelConfigVO();
        vo.setId(config.getId());
        vo.setName(config.getName());
        vo.setProvider(config.getProvider());
        vo.setApiUrl(config.getApiUrl());
        // 不返回API密钥
        vo.setModelName(config.getModelName());
        vo.setEmbeddingApiUrl(config.getEmbeddingApiUrl());
        vo.setEmbeddingModelName(config.getEmbeddingModelName());
        vo.setTemperature(config.getTemperature());
        vo.setMaxTokens(config.getMaxTokens());
        // 使用实体的 enabled 字段
        vo.setEnabled(config.getEnabled());
        vo.setIsDefault(config.getIsDefault());
        vo.setSortOrder(config.getSortOrder());
        vo.setDescription(config.getDescription());
        vo.setCreateTime(config.getCreateTime());
        vo.setUpdateTime(config.getUpdateTime());
        return vo;
    }

}

package com.aip.system.service.impl;

import com.aip.common.exception.BusinessException;
import com.aip.common.result.PageResult;
import com.aip.common.util.UuidV7Utils;
import com.aip.common.utils.ApiKeyEncryptUtil;
import com.aip.system.dto.ApiCredentialDTO;
import com.aip.system.dto.ApiCredentialStatsVO;
import com.aip.system.dto.ApiCredentialVO;
import com.aip.system.entity.SysApiCredential;
import com.aip.system.mapper.SysApiCredentialMapper;
import com.aip.system.service.ISysApiCredentialService;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * API凭证服务实现
 */
@Slf4j
@Service
public class SysApiCredentialServiceImpl implements ISysApiCredentialService {

    @Autowired
    private SysApiCredentialMapper apiCredentialMapper;

    @Override
    public PageResult<ApiCredentialVO> page(String keyword, int page, int size) {
        Page<SysApiCredential> credentialPage = apiCredentialMapper.findByKeyword(keyword, PageRequest.of(page - 1, size));

        List<ApiCredentialVO> voList = credentialPage.getContent().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        return PageResult.of(credentialPage.getTotalElements(), voList, (long) page, (long) size);
    }

    @Override
    public Optional<SysApiCredential> getById(String id) {
        return apiCredentialMapper.findById(id);
    }

    @Override
    public Optional<SysApiCredential> findByApiKey(String apiKey) {
        return apiCredentialMapper.findActiveByApiKey(apiKey);
    }

    @Override
    public Optional<SysApiCredential> findByAppId(String appId) {
        return apiCredentialMapper.findActiveByAppId(appId);
    }

    @Override
    @Transactional
    public ApiCredentialVO create(ApiCredentialDTO dto) {
        // 检查 AppId 唯一性
        if (existsByAppId(dto.getAppId())) {
            throw new BusinessException("应用ID已存在");
        }

        // 生成 API Key 和 Secret
        String apiKey = "AK" + UuidV7Utils.generateUuidV7String().toUpperCase().substring(0, 28);
        String rawSecret = UuidV7Utils.generateUuidV7String() + UuidV7Utils.generateUuidV7String();
        String encryptedSecret = ApiKeyEncryptUtil.encrypt(rawSecret);
        String secretLastFour = rawSecret.substring(rawSecret.length() - 4);

        SysApiCredential credential = new SysApiCredential();
        BeanUtils.copyProperties(dto, credential);
        credential.setApiKey(apiKey);
        credential.setApiSecret(encryptedSecret);
        credential.setApiSecretLastFour(secretLastFour);
        credential.setStatus(1);
        credential.setTotalCalls(0L);
        credential.setTodayCalls(0);
        credential.setMonthlyCalls(0L);
        credential.setDailyResetDate(LocalDate.now());
        credential.setMonthlyResetDate(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM")));

        if (dto.getAllowedExperts() != null && !dto.getAllowedExperts().isEmpty()) {
            credential.setAllowedExperts(JSON.toJSONString(dto.getAllowedExperts()));
        }

        if (credential.getRateLimitQps() == null) {
            credential.setRateLimitQps(10);
        }
        if (credential.getDailyQuota() == null) {
            credential.setDailyQuota(-1L);
        }
        if (credential.getMonthlyQuota() == null) {
            credential.setMonthlyQuota(-1L);
        }

        // 转换过期时间
        if (dto.getExpireTime() != null) {
            credential.setExpireTime(dto.getExpireTime().atZone(ZoneId.systemDefault()).toInstant());
        }

        apiCredentialMapper.save(credential);

        // 返回完整密钥（仅创建时返回）
        ApiCredentialVO vo = convertToVO(credential);
        vo.setApiSecret(rawSecret);
        vo.setApiSecretLastFour(secretLastFour);
        return vo;
    }

    @Override
    @Transactional
    public void update(String id, ApiCredentialDTO dto) {
        SysApiCredential credential = apiCredentialMapper.findById(id)
                .orElseThrow(() -> new BusinessException("凭证不存在"));

        // 检查 AppId 唯一性（排除自身）
        if (existsByAppId(dto.getAppId())) {
            Optional<SysApiCredential> existing = findByAppId(dto.getAppId());
            if (existing.isPresent() && !existing.get().getId().equals(id)) {
                throw new BusinessException("应用ID已存在");
            }
        }

        credential.setName(dto.getName());
        credential.setAppId(dto.getAppId());
        credential.setRateLimitQps(dto.getRateLimitQps());
        credential.setDailyQuota(dto.getDailyQuota());
        credential.setMonthlyQuota(dto.getMonthlyQuota());
        credential.setStatus(dto.getStatus());

        // 转换过期时间
        if (dto.getExpireTime() != null) {
            credential.setExpireTime(dto.getExpireTime().atZone(ZoneId.systemDefault()).toInstant());
        } else {
            credential.setExpireTime(null);
        }

        if (dto.getAllowedExperts() != null && !dto.getAllowedExperts().isEmpty()) {
            credential.setAllowedExperts(JSON.toJSONString(dto.getAllowedExperts()));
        } else {
            credential.setAllowedExperts(null);
        }

        apiCredentialMapper.save(credential);
    }

    @Override
    @Transactional
    public void delete(String id) {
        SysApiCredential credential = apiCredentialMapper.findById(id)
                .orElseThrow(() -> new BusinessException("凭证不存在"));
        credential.setDeleted(true);
        apiCredentialMapper.save(credential);
    }

    @Override
    @Transactional
    public void updateStatus(String id, Integer status) {
        SysApiCredential credential = apiCredentialMapper.findById(id)
                .orElseThrow(() -> new BusinessException("凭证不存在"));
        credential.setStatus(status);
        apiCredentialMapper.save(credential);
    }

    @Override
    @Transactional
    public ApiCredentialVO resetSecret(String id) {
        SysApiCredential credential = apiCredentialMapper.findById(id)
                .orElseThrow(() -> new BusinessException("凭证不存在"));

        // 生成新密钥
        String rawSecret = UuidV7Utils.generateUuidV7String() + UuidV7Utils.generateUuidV7String();
        String encryptedSecret = ApiKeyEncryptUtil.encrypt(rawSecret);
        String secretLastFour = rawSecret.substring(rawSecret.length() - 4);

        credential.setApiSecret(encryptedSecret);
        credential.setApiSecretLastFour(secretLastFour);
        apiCredentialMapper.save(credential);

        // 返回新密钥
        ApiCredentialVO vo = convertToVO(credential);
        vo.setApiSecret(rawSecret);
        return vo;
    }

    @Override
    public ApiCredentialStatsVO getStats(String id) {
        SysApiCredential credential = apiCredentialMapper.findById(id)
                .orElseThrow(() -> new BusinessException("凭证不存在"));

        ApiCredentialStatsVO stats = new ApiCredentialStatsVO();
        stats.setId(credential.getId());
        stats.setName(credential.getName());
        stats.setAppId(credential.getAppId());
        stats.setTotalCalls(credential.getTotalCalls());
        stats.setTodayCalls(credential.getTodayCalls());
        stats.setMonthlyCalls(credential.getMonthlyCalls());
        stats.setLastCalledAt(credential.getLastCalledAt() != null ?
                LocalDateTime.ofInstant(credential.getLastCalledAt(), ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : null);

        // 计算额度使用率
        if (credential.getDailyQuota() != null && credential.getDailyQuota() > 0) {
            stats.setDailyUsagePercent((double) credential.getTodayCalls() / credential.getDailyQuota() * 100);
            stats.setDailyRemaining(credential.getDailyQuota() - credential.getTodayCalls());
        } else {
            stats.setDailyUsagePercent(0.0);
            stats.setDailyRemaining(-1L);
        }

        if (credential.getMonthlyQuota() != null && credential.getMonthlyQuota() > 0) {
            stats.setMonthlyUsagePercent((double) credential.getMonthlyCalls() / credential.getMonthlyQuota() * 100);
            stats.setMonthlyRemaining(credential.getMonthlyQuota() - credential.getMonthlyCalls());
        } else {
            stats.setMonthlyUsagePercent(0.0);
            stats.setMonthlyRemaining(-1L);
        }

        return stats;
    }

    @Override
    public List<SysApiCredential> listActive() {
        return apiCredentialMapper.findAllActive();
    }

    @Override
    public boolean existsByApiKey(String apiKey) {
        return apiCredentialMapper.existsByApiKey(apiKey);
    }

    @Override
    public boolean existsByAppId(String appId) {
        return apiCredentialMapper.existsByAppId(appId);
    }

    /**
     * 转换实体为VO
     */
    private ApiCredentialVO convertToVO(SysApiCredential credential) {
        ApiCredentialVO vo = new ApiCredentialVO();
        vo.setId(credential.getId());
        vo.setName(credential.getName());
        vo.setAppId(credential.getAppId());
        vo.setApiKey(credential.getApiKey());
        vo.setApiSecretLastFour(credential.getApiSecretLastFour());
        vo.setRateLimitQps(credential.getRateLimitQps());
        vo.setDailyQuota(credential.getDailyQuota());
        vo.setMonthlyQuota(credential.getMonthlyQuota());
        vo.setStatus(credential.getStatus());
        vo.setStatusText(credential.getStatus() != null && credential.getStatus() == 1 ? "启用" : "禁用");
        vo.setExpireTime(credential.getExpireTime() != null ?
                LocalDateTime.ofInstant(credential.getExpireTime(), ZoneId.systemDefault()) : null);
        vo.setTotalCalls(credential.getTotalCalls());
        vo.setTodayCalls(credential.getTodayCalls());
        vo.setMonthlyCalls(credential.getMonthlyCalls());
        vo.setLastCalledAt(credential.getLastCalledAt() != null ?
                LocalDateTime.ofInstant(credential.getLastCalledAt(), ZoneId.systemDefault()) : null);
        vo.setCreateTime(credential.getCreateTime() != null ?
                LocalDateTime.ofInstant(credential.getCreateTime(), ZoneId.systemDefault()) : null);

        // 解析允许的专家列表
        if (credential.getAllowedExperts() != null && !credential.getAllowedExperts().isEmpty()) {
            try {
                vo.setAllowedExperts(JSON.parseArray(credential.getAllowedExperts(), String.class));
            } catch (Exception e) {
                log.warn("解析allowedExperts失败: {}", credential.getAllowedExperts());
            }
        }

        return vo;
    }
}

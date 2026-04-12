package com.aip.system.entity;

import com.aip.common.entity.BusinessEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 开放平台 API 凭证实体
 */
@Getter
@Setter
@Entity
@Table(name = "t_sys_api_credential")
public class SysApiCredential extends BusinessEntity {

    /** 凭证名称 */
    @Column(nullable = false, length = 100)
    private String name;

    /** 应用ID（唯一标识） */
    @Column(nullable = false, unique = true, length = 50)
    private String appId;

    /** API Key（AK） */
    @Column(nullable = false, unique = true, length = 64)
    private String apiKey;

    /** API Secret（SK，加密存储） */
    @Column(nullable = false, length = 128)
    private String apiSecret;

    /** 密钥后四位 */
    @Column(length = 4)
    private String apiSecretLastFour;

    /** 允许调用的专家编码列表（JSON） */
    @Column(columnDefinition = "JSON")
    private String allowedExperts;

    /** 每秒最大请求数 */
    private Integer rateLimitQps = 10;

    /** 每日调用额度（-1=不限） */
    private Long dailyQuota = -1L;

    /** 每月调用额度（-1=不限） */
    private Long monthlyQuota = -1L;

    /** 每日计数重置日期 */
    private LocalDate dailyResetDate;

    /** 每月计数重置日期 */
    @Column(length = 7)
    private String monthlyResetDate;

    /** 总调用次数 */
    private Long totalCalls = 0L;

    /** 今日调用次数 */
    private Integer todayCalls = 0;

    /** 本月调用次数 */
    private Long monthlyCalls = 0L;

    /** 最后调用时间（毫秒精度） */
    @Column(columnDefinition = "TIMESTAMP(3)")
    private Instant lastCalledAt;

    /** 过期时间（毫秒精度） */
    @Column(columnDefinition = "TIMESTAMP(3)")
    private Instant expireTime;

    // ========== 辅助方法 ==========

    /**
     * 判断凭证是否有效
     */
    public boolean isValid() {
        if (getStatus() == null || getStatus() != 1) {
            return false;
        }
        if (expireTime != null && expireTime.isBefore(Instant.now())) {
            return false;
        }
        return true;
    }

    /**
     * 获取允许的专家列表
     */
    public List<String> getAllowedExpertList() {
        if (allowedExperts == null || allowedExperts.isEmpty() || "null".equals(allowedExperts)) {
            return null;
        }
        try {
            return com.alibaba.fastjson2.JSON.parseArray(allowedExperts, String.class);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 判断是否允许调用指定专家
     */
    public boolean canAccessExpert(String expertCode) {
        List<String> allowed = getAllowedExpertList();
        if (allowed == null || allowed.isEmpty()) {
            return true;
        }
        return allowed.contains(expertCode);
    }

    /**
     * 获取显示用的密钥前缀（后四位）
     */
    public String getDisplaySecret() {
        if (apiSecretLastFour != null) {
            return "****" + apiSecretLastFour;
        }
        return "****";
    }
}

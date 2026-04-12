package com.aip.system.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * API凭证响应VO
 */
@Data
public class ApiCredentialVO {

    /** 凭证ID（UUIDv7 无横杠字符串） */
    private String id;

    /** 凭证名称 */
    private String name;

    /** 应用ID */
    private String appId;

    /** API Key */
    private String apiKey;

    /** API Secret（创建时返回完整密钥，之后只显示后四位） */
    private String apiSecret;

    /** 密钥后四位 */
    private String apiSecretLastFour;

    /** 允许的专家列表 */
    private List<String> allowedExperts;

    /** QPS限制 */
    private Integer rateLimitQps;

    /** 每日额度 */
    private Long dailyQuota;

    /** 每月额度 */
    private Long monthlyQuota;

    /** 状态 */
    private Integer status;

    /** 状态文字 */
    private String statusText;

    /** 过期时间 */
    private LocalDateTime expireTime;

    /** 总调用次数 */
    private Long totalCalls;

    /** 今日调用次数 */
    private Integer todayCalls;

    /** 本月调用次数 */
    private Long monthlyCalls;

    /** 最后调用时间 */
    private LocalDateTime lastCalledAt;

    /** 创建时间 */
    private LocalDateTime createTime;
}

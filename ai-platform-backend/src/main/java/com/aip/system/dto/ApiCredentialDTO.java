package com.aip.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * API凭证请求DTO
 */
@Data
public class ApiCredentialDTO {

    /** 凭证ID（更新时使用） */
    private Long id;

    /** 凭证名称 */
    @NotBlank(message = "凭证名称不能为空")
    private String name;

    /** 应用ID */
    @NotBlank(message = "应用ID不能为空")
    private String appId;

    /** 允许调用的专家ID列表（null=所有专家） */
    private List<String> allowedExperts;

    /** 每秒最大请求数 */
    private Integer rateLimitQps = 10;

    /** 每日调用额度（-1=不限） */
    private Long dailyQuota = -1L;

    /** 每月调用额度（-1=不限） */
    private Long monthlyQuota = -1L;

    /** 状态：1-启用，0-禁用 */
    private Integer status = 1;

    /** 过期时间（null=永不过期） */
    private LocalDateTime expireTime;
}

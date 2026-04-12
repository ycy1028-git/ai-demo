package com.aip.ai.entity;

import com.aip.common.entity.BusinessEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * AI大模型配置实体
 * 用于存储不同大模型的API配置信息，支持多模型切换
 */
@Getter
@Setter
@Entity
@Table(name = "t_ai_model_config")
public class AiModelConfig extends BusinessEntity {

    /** 模型名称（显示用） */
    @Column(nullable = false, length = 100)
    private String name;

    /** 提供商：qwen/deepseek/zhipu/openai */
    @Column(nullable = false, length = 50)
    private String provider;

    /** API地址 */
    @Column(length = 500)
    private String apiUrl;

    /** API密钥（加密存储） */
    @Column(length = 255)
    private String apiKey;

    /** 模型标识（如 deepseek-chat、qwen-plus） */
    @Column(nullable = false, length = 100)
    private String modelName;

    /** 温度参数（0-1） */
    @Column(precision = 3, scale = 2)
    private BigDecimal temperature = new BigDecimal("0.7");

    /** 最大生成Token数 */
    private Integer maxTokens = 2000;

    /** 是否启用 */
    @Column(nullable = false)
    private Boolean enabled = true;

    /** 是否为默认模型 */
    @Column(nullable = false)
    private Boolean isDefault = false;

    /** 排序权重 */
    @Column(nullable = false)
    private Integer sortOrder = 0;

    /** 模型描述 */
    @Column(length = 500)
    private String description;

    /**
     * 获取完整的API地址
     */
    public String getFullApiUrl() {
        if (apiUrl == null || apiUrl.isEmpty()) {
            return getDefaultApiUrl();
        }
        if (apiUrl.contains("/chat/completions")) {
            return apiUrl;
        }
        return apiUrl.endsWith("/") ? apiUrl + "chat/completions" : apiUrl + "/chat/completions";
    }

    /**
     * 获取提供商的默认API地址
     */
    private String getDefaultApiUrl() {
        return switch (provider.toLowerCase()) {
            case "deepseek" -> "https://api.deepseek.com/v1/chat/completions";
            case "qwen", "dashscope" -> "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";
            case "zhipu" -> "https://open.bigmodel.cn/api/paas/v4/chat/completions";
            case "openai" -> "https://api.openai.com/v1/chat/completions";
            default -> "";
        };
    }
}

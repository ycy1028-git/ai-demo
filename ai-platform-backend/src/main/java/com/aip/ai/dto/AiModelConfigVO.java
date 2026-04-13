package com.aip.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * AI模型配置响应VO
 */
@Data
@Schema(description = "AI模型配置响应")
public class AiModelConfigVO {

    @Schema(description = "模型ID（UUIDv7 无横杠字符串）")
    private String id;

    @Schema(description = "模型名称")
    private String name;

    @Schema(description = "提供商")
    private String provider;

    @Schema(description = "API地址")
    private String apiUrl;

    @Schema(description = "模型标识")
    private String modelName;

    @Schema(description = "Embedding 接口地址")
    private String embeddingApiUrl;

    @Schema(description = "Embedding 模型标识")
    private String embeddingModelName;

    @Schema(description = "温度参数")
    private BigDecimal temperature;

    @Schema(description = "最大Token数")
    private Integer maxTokens;

    @Schema(description = "是否启用")
    private Boolean enabled;

    @Schema(description = "是否为默认模型")
    private Boolean isDefault;

    @Schema(description = "排序权重")
    private Integer sortOrder;

    @Schema(description = "模型描述")
    private String description;

    @Schema(description = "创建时间")
    private Instant createTime;

    @Schema(description = "更新时间")
    private Instant updateTime;
}

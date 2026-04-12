package com.aip.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * AI模型配置请求DTO
 */
@Data
@Schema(description = "AI模型配置请求")
public class AiModelConfigDTO {

    @Schema(description = "模型ID（更新时必填）")
    private String id;

    @Schema(description = "模型名称", example = "DeepSeek Chat")
    @NotBlank(message = "模型名称不能为空")
    @Size(max = 100, message = "模型名称不能超过100字符")
    private String name;

    @Schema(description = "提供商", example = "deepseek")
    @NotBlank(message = "提供商不能为空")
    @Size(max = 50, message = "提供商不能超过50字符")
    private String provider;

    @Schema(description = "API地址", example = "https://api.deepseek.com/v1")
    @Size(max = 500, message = "API地址不能超过500字符")
    private String apiUrl;

    @Schema(description = "API密钥")
    @Size(max = 255, message = "API密钥不能超过255字符")
    private String apiKey;

    @Schema(description = "模型标识", example = "deepseek-chat")
    @NotBlank(message = "模型标识不能为空")
    @Size(max = 100, message = "模型标识不能超过100字符")
    private String modelName;

    @Schema(description = "温度参数", example = "0.7")
    @DecimalMin(value = "0.0", message = "温度参数最小值为0")
    @DecimalMax(value = "2.0", message = "温度参数最大值为2")
    private BigDecimal temperature = new BigDecimal("0.7");

    @Schema(description = "最大Token数", example = "2000")
    @Min(value = 1, message = "最大Token数最小值为1")
    @Max(value = 128000, message = "最大Token数最大值为128000")
    private Integer maxTokens = 2000;

    @Schema(description = "是否启用", example = "true")
    private Boolean enabled = true;

    @Schema(description = "是否为默认模型", example = "false")
    private Boolean isDefault = false;

    @Schema(description = "排序权重", example = "1")
    @Min(value = 0, message = "排序权重最小值为0")
    private Integer sortOrder = 0;

    @Schema(description = "模型描述")
    @Size(max = 500, message = "模型描述不能超过500字符")
    private String description;
}

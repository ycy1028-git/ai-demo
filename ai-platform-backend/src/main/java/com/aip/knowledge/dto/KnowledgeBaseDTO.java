package com.aip.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 知识库创建/更新DTO
 */
@Data
public class KnowledgeBaseDTO {

    /** 主键ID（更新时必填） */
    private String id;

    /** 知识库名称 */
    @NotBlank(message = "知识库名称不能为空")
    @Size(max = 100, message = "知识库名称不能超过100个字符")
    private String name;

    /** 知识库编码 */
    @NotBlank(message = "知识库编码不能为空")
    @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_]{2,48}$", message = "编码必须以字母开头，长度3-50位")
    private String code;

    /** 描述 */
    @Size(max = 500, message = "描述不能超过500个字符")
    private String description;

    /** 文本索引名（自动生成或指定） */
    @Pattern(regexp = "^[a-z0-9][a-z0-9_-]{2,63}$", message = "索引名称需为3-64位小写字母、数字、短横线或下划线")
    private String esIndex;

    /** 向量索引名（可选，不填默认与文本索引一致） */
    @Pattern(regexp = "^[a-z0-9][a-z0-9_-]{2,63}$", message = "向量索引名称需为3-64位小写字母、数字、短横线或下划线")
    private String vectorIndex;

    /** MinIO 桶名称（可选） */
    @Pattern(regexp = "^[a-z0-9](?:[a-z0-9.-]{1,61}[a-z0-9])$", message = "桶名称需为3-63位小写字母、数字、点或短横线，且以字母或数字开头结尾")
    private String bucketName;

    /** 状态：0禁用 1启用 */
    private Integer status = 1;

    /**
     * 关联的AI大模型配置ID（UUIDv7 无横杠字符串）
     */
    private String modelConfigId;

    /**
     * 业务场景描述
     */
    @Size(max = 500, message = "业务场景描述不能超过500个字符")
    private String sceneDescription;

    /**
     * 匹配优先级
     */
    private Integer priority = 0;
}

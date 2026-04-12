package com.aip.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 知识条目创建/更新DTO
 */
@Data
public class KnowledgeItemDTO {

    /** 主键ID（更新时必填） */
    private String id;

    /** 所属知识库ID（UUIDv7 无横杠字符串） */
    @NotBlank(message = "知识库ID不能为空")
    private String kbId;

    /** 知识标题 */
    @NotBlank(message = "知识标题不能为空")
    private String title;

    /** 知识内容 */
    @NotBlank(message = "知识内容不能为空")
    private String content;

    /** 摘要 */
    private String summary;

    /** 标签列表 */
    private List<String> tags;

    /** 状态：0草稿 1已发布 */
    private Integer status = 1;

    /** 来源类型 */
    private String sourceType = "manual";

    /** 来源文档ID（UUIDv7 无横杠字符串） */
    private String sourceDocId;

    /** MinIO文件路径 */
    private String minioPath;

    /** 原始文件名 */
    private String originalFileName;

    /** 文件类型 */
    private String fileType;
}

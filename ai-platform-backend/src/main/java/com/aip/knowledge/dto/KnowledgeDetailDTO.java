package com.aip.knowledge.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 知识详情DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeDetailDTO {

    /** 知识条目ID */
    private String id;

    /** 所属知识库ID */
    private String kbId;

    /** 所属知识库名称 */
    private String kbName;

    /** 知识标题 */
    private String title;

    /** 知识内容 */
    private String content;

    /** 摘要 */
    private String summary;

    /** 标签列表 */
    private List<String> tags;

    /** 来源类型：manual/upload */
    private String sourceType;

    /** 原始文件名（如果有） */
    private String originalFileName;

    /** 文件类型 */
    private String fileType;

    /** MinIO文件路径 */
    private String minioPath;

    /** 状态：0草稿 1已发布 */
    private Integer status;

    /** 创建时间 */
    private String createTime;

    /** 更新时间 */
    private String updateTime;
}

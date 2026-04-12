package com.aip.knowledge.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 知识检索结果DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeSearchResultDTO {

    /** 知识条目ID */
    private String id;

    /** 知识标题 */
    private String title;

    /** 知识内容 */
    private String content;

    /** 摘要 */
    private String summary;

    /** 所属知识库ID */
    private String kbId;

    /** 所属知识库名称 */
    private String kbName;

    /** 来源类型：manual/upload */
    private String sourceType;

    /** 来源文档ID */
    private String sourceDocId;

    /** 原始文件名 */
    private String originalFileName;

    /** 文件类型 */
    private String fileType;

    /** 匹配分数/相似度 */
    private Double score;

    /** 标签列表 */
    private List<String> tags;

    /** 创建时间 */
    private String createTime;
}

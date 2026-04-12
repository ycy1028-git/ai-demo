package com.aip.knowledge.entity;

import com.aip.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * 知识条目实体
 */
@Getter
@Setter
@Entity
@Table(name = "t_kb_knowledge_item")
public class KnowledgeItem extends BaseEntity {

    /** 所属知识库ID */
    @Column(nullable = false, length = 32)
    private String kbId;

    /** 知识标题 */
    @Column(nullable = false, length = 200)
    private String title;

    /** 知识内容 */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** 摘要 */
    @Column(length = 500)
    private String summary;

    /** 标签（JSON） */
    @Column(columnDefinition = "JSON")
    private String tags;

    /** 状态：0草稿 1已发布 */
    @Column(nullable = false)
    private Integer status = 1;

    /** 向量化状态：0未处理 1处理中 2已完成 3失败 */
    @Column(nullable = false)
    private Integer vectorStatus = 0;

    /** 向量化分块数 */
    private Integer vectorChunks = 0;

    /** 来源类型：manual/upload */
    @Column(nullable = false, length = 20)
    private String sourceType = "manual";

    /** 来源文档ID */
    @Column(length = 32)
    private String sourceDocId;

    /** MinIO文件路径 */
    @Column(length = 500)
    private String minioPath;

    /** 原始文件名 */
    @Column(length = 255)
    private String originalFileName;

    /** 文件类型 */
    @Column(length = 50)
    private String fileType;
}

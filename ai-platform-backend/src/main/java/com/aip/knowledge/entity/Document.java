package com.aip.knowledge.entity;

import com.aip.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * 文档实体
 */
@Getter
@Setter
@Entity
@Table(name = "t_kb_document")
public class Document extends BaseEntity {

    /** 所属知识库ID */
    @Column(nullable = false, length = 32)
    private String kbId;

    /** 文档名称 */
    @Column(nullable = false, length = 200)
    private String name;

    /** 原始文件名 */
    @Column(nullable = false, length = 255)
    private String originalName;

    /** 文件类型 */
    @Column(nullable = false, length = 20)
    private String fileType;

    /** 文件大小（字节） */
    @Column(nullable = false)
    private Long fileSize;

    /** MinIO存储路径 */
    @Column(nullable = false, length = 500)
    private String minioPath;

    /** 提取状态：0待处理 1提取中 2完成 3失败 */
    @Column(nullable = false)
    private Integer extractStatus = 0;

    /** 提取的文本 */
    @Column(columnDefinition = "LONGTEXT")
    private String extractText;

    /** 页数 */
    private Integer pageCount;

    /** 分块数 */
    private Integer chunkCount;

    /** 错误信息 */
    @Column(length = 500)
    private String errorMsg;
}

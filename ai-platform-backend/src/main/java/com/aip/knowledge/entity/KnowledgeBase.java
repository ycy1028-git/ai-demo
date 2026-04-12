package com.aip.knowledge.entity;

import com.aip.common.entity.BusinessEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * 知识库实体
 */
@Getter
@Setter
@Entity
@Table(name = "t_kb_knowledge_base")
public class KnowledgeBase extends BusinessEntity {

    /** 知识库名称 */
    @Column(nullable = false, length = 100)
    private String name;

    /** 知识库编码 */
    @Column(nullable = false, unique = true, length = 50)
    private String code;

    /** 知识库描述 */
    @Column(length = 500)
    private String description;

    /** ES索引名 */
    @Column(nullable = false, length = 100)
    private String esIndex;

    /** OSS存储路径前缀 */
    @Column(length = 255)
    private String ossPathPrefix = "documents/";

    /**
     * 关联的AI大模型配置ID
     */
    @Column(length = 32)
    private String modelConfigId;

    /**
     * 业务场景描述
     */
    @Column(length = 500)
    private String sceneDescription;

    /**
     * 匹配优先级（数值越大优先级越高）
     */
    @Column(nullable = false)
    private Integer priority = 0;

    /**
     * 知识条目数量（非持久化字段）
     */
    @Transient
    private Integer itemCount = 0;
}

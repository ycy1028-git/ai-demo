package com.aip.flow.entity;

import com.aip.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * 流程模板实体
 * 替代原有的 ExpertFlow，移除 expert_id 关联，流程直接独立存在
 *
 * 继承 BaseEntity，自动获得：
 * - UUIDv7 主键（无横杠字符串）
 * - 审计字段
 * - 逻辑删除
 * - 乐观锁
 */
@Getter
@Setter
@Entity
@Table(name = "t_flow_template")
public class FlowTemplate extends BaseEntity {

    /** 模板编码 */
    @Column(nullable = false, unique = true, length = 64)
    private String templateCode;

    /** 模板名称 */
    @Column(nullable = false, length = 128)
    private String templateName;

    /** 描述说明 */
    @Column(length = 512)
    private String description;

    /** 匹配模式关键词（用于快速匹配） */
    @Column(length = 256)
    private String matchPattern;

    /** 匹配提示词（LLM理解用） */
    @Column(columnDefinition = "TEXT")
    private String matchPrompt;

    /** 完整流程定义（包含节点编排） */
    @Column(columnDefinition = "JSON")
    private String flowData;

    /** 优先级（高优先级优先匹配） */
    @Column(nullable = false)
    private Integer priority = 0;

    /** 是否为兜底模板：0-否，1-是 */
    @Column(nullable = false)
    private Integer isFallback = 0;

    /** 是否支持动态规划：0-否，1-是 */
    @Column(nullable = false)
    private Integer isDynamic = 1;

    /** 状态：0禁用 1启用 */
    @Column(nullable = false)
    private Integer status = 1;

    /** 发布时间（毫秒精度） */
    @Column(columnDefinition = "TIMESTAMP(3)")
    private Instant publishedAt;
}

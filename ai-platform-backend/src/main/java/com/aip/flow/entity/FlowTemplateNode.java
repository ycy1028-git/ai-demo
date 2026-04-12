package com.aip.flow.entity;

import com.aip.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * 节点编排关联实体
 * 用于存储模板与节点的关联关系
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
@Table(name = "t_flow_template_node")
public class FlowTemplateNode extends BaseEntity {

    /** 模板ID */
    @Column(nullable = false, length = 32)
    private String templateId;

    /** 节点ID */
    @Column(nullable = false)
    private Long nodeId;

    /** 节点执行顺序号 */
    @Column(nullable = false)
    private Integer executionOrder = 0;

    /** 节点运行时配置 */
    @Column(columnDefinition = "JSON")
    private String nodeConfig;
}

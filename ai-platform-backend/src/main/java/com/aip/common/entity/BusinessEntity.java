package com.aip.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

/**
 * 业务实体基类
 * 所有业务实体应继承此类
 * 
 * 继承自 BaseEntity，自动获得：
 * - UUIDv7 主键
 * - 审计字段
 * - 逻辑删除
 * - 乐观锁
 * 
 * 新增业务公共字段：
 * - status: 实体状态
 */
@Getter
@Setter
@MappedSuperclass
public abstract class BusinessEntity extends BaseEntity {

    /**
     * 实体状态：0-禁用，1-启用
     */
    @Column(nullable = false)
    private Integer status = 1;

    /**
     * 是否启用
     */
    public boolean isEnabled() {
        return this.status != null && this.status == 1;
    }

    /**
     * 启用实体
     */
    public void enable() {
        this.status = 1;
    }

    /**
     * 禁用实体
     */
    public void disable() {
        this.status = 0;
    }
}

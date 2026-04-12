package com.aip.common.entity;

import com.aip.common.util.UuidV7Utils;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * 实体基类，统一管理公共字段
 *
 * 命名策略：
 * - 表名：通过 @Table 自动添加 t_ 前缀
 * - 字段名：通过 CustomPhysicalNamingStrategy 自动添加 f_ 前缀
 *
 * 公共字段：
 * - UUIDv7 主键（无横杠字符串）：趋势递增、索引友好、全局唯一
 * - 审计字段：createTime、updateTime、createBy、updateBy
 * - 逻辑删除：deleted 标志
 * - 乐观锁：version 防止并发冲突
 *
 * 存储格式：
 * - 主键：CHAR(32) 无横杠小写字符串，如 01920a1e7d2c7abc8f3e1a2b3c4d5e6f
 * - createBy/updateBy：CHAR(32) 无横杠小写字符串
 */
@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    /**
     * 主键：UUIDv7 无横杠字符串
     * 数据库存储为 CHAR(32)，时间有序追加，索引性能最佳
     */
    @Id
    @Column(length = 32, updatable = false, nullable = false)
    private String id;

    @CreationTimestamp
    @Column(nullable = false, updatable = false, columnDefinition = "TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP(3)")
    private Instant createTime;

    @UpdateTimestamp
    @Column(nullable = false, columnDefinition = "TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)")
    private Instant updateTime;

    /**
     * 创建人 ID（UUIDv7 无横杠字符串）
     */
    @Column(updatable = false, length = 32)
    private String createBy;

    /**
     * 更新人 ID（UUIDv7 无横杠字符串）
     */
    @Column(length = 32)
    private String updateBy;

    @Version
    private Long version;

    @Column(nullable = false)
    private Boolean deleted = false;

    /**
     * 前置保存回调
     * 自动生成 UUIDv7 无横杠字符串作为主键
     */
    @PrePersist
    protected void onCreate() {
        if (this.id == null) {
            this.id = UuidV7Utils.generateUuidV7String();
        }
        if (this.deleted == null) {
            this.deleted = false;
        }
        if (this.version == null) {
            this.version = 0L;
        }
    }

    /**
     * 逻辑删除标记
     */
    public void markDeleted() {
        this.deleted = true;
    }

    /**
     * 恢复删除标记
     */
    public void markUndeleted() {
        this.deleted = false;
    }

    /**
     * 是否已删除
     */
    public boolean isDeleted() {
        return Boolean.TRUE.equals(this.deleted);
    }

    /**
     * 检查实体是否有效（未删除）
     */
    public boolean isActive() {
        return !isDeleted();
    }

    /**
     * 获取 UUID 对象格式的主键
     * @return UUID 对象
     */
    public java.util.UUID getUuid() {
        return id != null ? UuidV7Utils.fromShortString(id) : null;
    }

    /**
     * 获取无横杠字符串格式的主键
     * @return 32位无横杠字符串
     */
    public String getId() {
        return id;
    }
}

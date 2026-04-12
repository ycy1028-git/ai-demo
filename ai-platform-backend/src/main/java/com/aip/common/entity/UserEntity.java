package com.aip.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * 用户实体基类
 * 
 * 继承自 BusinessEntity，自动获得：
 * - UUIDv7 主键
 * - 审计字段
 * - 逻辑删除
 * - 乐观锁
 * - 状态字段
 * 
 * 新增用户专属字段：
 * - username: 用户名
 * - password: 密码（BCrypt 加密）
 * - realName: 真实姓名
 * - email: 邮箱
 * - phone: 手机号
 * - avatar: 头像
 * - lastLoginAt: 最后登录时间
 * - lastLoginIp: 最后登录IP
 */
@Getter
@Setter
@MappedSuperclass
public abstract class UserEntity extends BusinessEntity {

    /**
     * 用户名（唯一）
     */
    @Column(nullable = false, unique = true, length = 50)
    private String username;

    /**
     * 密码（BCrypt 加密存储）
     */
    @Column(nullable = false, length = 255)
    private String password;

    /**
     * 真实姓名
     */
    @Column(nullable = false, length = 50)
    private String realName;

    /**
     * 邮箱
     */
    @Column(length = 100)
    private String email;

    /**
     * 手机号
     */
    @Column(length = 20)
    private String phone;

    /**
     * 头像URL
     */
    @Column(length = 500)
    private String avatar;

    /**
     * 最后登录时间
     */
    @Column(columnDefinition = "TIMESTAMP(3)")
    private Instant lastLoginAt;

    /**
     * 最后登录IP
     */
    @Column(length = 50)
    private String lastLoginIp;

    /**
     * 是否为禁用状态
     */
    public boolean isDisabled() {
        return getStatus() == null || getStatus() == 0;
    }

    /**
     * 是否为正常状态
     */
    public boolean isActive() {
        return getStatus() != null && getStatus() == 1 && !isDeleted();
    }
}

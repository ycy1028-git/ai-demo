package com.aip.system.entity;

import com.aip.common.entity.UserEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * 系统用户实体
 * 继承自 UserEntity，自动获得：
 * - UUIDv7 主键（无横杠字符串）
 * - 审计字段、逻辑删除、乐观锁
 * - 用户名、密码、状态等用户专属字段
 */
@Getter
@Setter
@Entity
@Table(name = "t_sys_user")
public class SysUser extends UserEntity {
    // 继承 UserEntity 的所有字段，无需重复定义
    // 如需扩展用户专属字段，在此添加
}

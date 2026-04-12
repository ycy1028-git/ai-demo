package com.aip.system.entity;

import com.aip.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * 操作日志实体
 */
@Getter
@Setter
@Entity
@Table(name = "t_sys_operation_log")
public class SysOperationLog extends BaseEntity {

    /** 用户ID */
    @Column(length = 32)
    private String userId;

    /** 用户名 */
    @Column(length = 50)
    private String username;

    /** 操作类型 */
    @Column(nullable = false, length = 50)
    private String operationType;

    /** 操作对象类型 */
    @Column(nullable = false, length = 50)
    private String objectType;

    /** 操作对象ID */
    @Column(length = 32)
    private String objectId;

    /** 操作描述 */
    @Column(length = 500)
    private String description;

    /** 请求参数 */
    @Column(columnDefinition = "TEXT")
    private String requestParams;

    /** 请求方法 */
    @Column(length = 10)
    private String requestMethod;

    /** 请求URL */
    @Column(length = 500)
    private String requestUrl;

    /** IP地址 */
    @Column(length = 50)
    private String ip;

    /** 执行结果：SUCCESS/FAIL */
    @Column(length = 20)
    private String result;

    /** 错误信息 */
    @Column(columnDefinition = "TEXT")
    private String errorMsg;

    /** 执行时间（毫秒） */
    private Long executionTime;
}

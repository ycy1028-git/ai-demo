package com.aip.app.entity;

import com.aip.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * 调用统计实体
 */
@Getter
@Setter
@Entity
@Table(name = "t_app_invocation_stat", indexes = {
    @Index(name = "idx_stat_date", columnList = "f_stat_date"),
    @Index(name = "idx_assistant_id", columnList = "f_assistant_id"),
    @Index(name = "idx_user_id", columnList = "f_user_id")
})
public class InvocationStat extends BaseEntity {

    /** 统计日期（格式：yyyy-MM-dd） */
    @Column(nullable = false, length = 10)
    private String statDate;

    /** 助手ID */
    @Column(nullable = false, length = 32)
    private String assistantId;

    /** 助手编码 */
    @Column(nullable = false, length = 50)
    private String assistantCode;

    /** 用户ID（可为null表示未登录用户） */
    @Column(length = 32)
    private String userId;

    /** 调用次数 */
    @Column(nullable = false)
    private Long invokeCount = 0L;

    /** 成功次数 */
    @Column(nullable = false)
    private Long successCount = 0L;

    /** 失败次数 */
    @Column(nullable = false)
    private Long failCount = 0L;

    /** 总Token消耗 */
    @Column(nullable = false)
    private Long totalTokens = 0L;

    /** 平均响应时间（毫秒） */
    @Column(nullable = false)
    private Long avgResponseTime = 0L;

    /** 累计响应时间（用于计算平均值） */
    @Column(nullable = false)
    private Long totalResponseTime = 0L;
}

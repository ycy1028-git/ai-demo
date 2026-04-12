package com.aip.system.dto;

import lombok.Data;

/**
 * 凭证统计VO
 */
@Data
public class ApiCredentialStatsVO {

    /** 凭证ID（UUIDv7 无横杠字符串） */
    private String id;

    /** 凭证名称 */
    private String name;

    /** 应用ID */
    private String appId;

    /** 总调用次数 */
    private Long totalCalls;

    /** 今日调用次数 */
    private Integer todayCalls;

    /** 本月调用次数 */
    private Long monthlyCalls;

    /** 今日额度使用率 */
    private Double dailyUsagePercent;

    /** 本月额度使用率 */
    private Double monthlyUsagePercent;

    /** 最后调用时间 */
    private String lastCalledAt;

    /** 今日剩余额度 */
    private Long dailyRemaining;

    /** 本月剩余额度 */
    private Long monthlyRemaining;
}

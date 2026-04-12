package com.aip.app.service;

import com.aip.app.dto.StatQueryDTO;
import com.aip.app.entity.InvocationStat;

import java.util.List;

/**
 * 调用统计服务接口
 */
public interface IInvocationStatService {

    /**
     * 记录调用
     *
     * @param statDate     统计日期
     * @param assistantId  助手ID
     * @param assistantCode 助手编码
     * @param userId       用户ID
     * @param invokeCount  调用次数
     * @param successCount 成功次数
     * @param failCount    失败次数
     * @param tokens       Token数量
     * @param responseTime 响应时间
     */
    void recordInvocation(String statDate, String assistantId, String assistantCode,
                          String userId, Long invokeCount, Long successCount,
                          Long failCount, Long tokens, Long responseTime);

    /**
     * 获取今日统计
     *
     * @param assistantId 助手ID
     * @return 今日统计
     */
    InvocationStat getTodayStat(String assistantId);

    /**
     * 获取指定日期统计
     *
     * @param statDate    日期
     * @param assistantId 助手ID
     * @return 统计记录
     */
    InvocationStat getStatByDate(String statDate, String assistantId);

    /**
     * 获取日期范围统计
     *
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @return 统计列表
     */
    List<InvocationStat> getStatsByDateRange(String startDate, String endDate);

    /**
     * 获取助手的所有统计记录
     *
     * @param assistantId 助手ID
     * @return 统计列表
     */
    List<InvocationStat> getStatsByAssistant(String assistantId);

    /**
     * 获取用户的统计记录
     *
     * @param userId 用户ID
     * @return 统计列表
     */
    List<InvocationStat> getStatsByUser(String userId);

    /**
     * 按条件查询统计
     *
     * @param query 查询条件
     * @return 统计列表
     */
    List<InvocationStat> queryStats(StatQueryDTO query);

    /**
     * 获取今日总调用量
     *
     * @return 今日总调用量
     */
    Long getTodayTotalInvokeCount();

    /**
     * 获取助手累计调用量
     *
     * @param assistantId 助手ID
     * @return 累计调用量
     */
    Long getAssistantTotalInvokeCount(String assistantId);

    /**
     * 获取助手今日调用量
     *
     * @param assistantId 助手ID
     * @return 今日调用量
     */
    Long getAssistantTodayInvokeCount(String assistantId);
}

package com.aip.app.mapper;

import com.aip.app.entity.InvocationStat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 调用统计Mapper
 */
@Repository
public interface InvocationStatMapper extends JpaRepository<InvocationStat, String> {

    /**
     * 根据日期和助手ID查询
     */
    Optional<InvocationStat> findByStatDateAndAssistantId(String statDate, String assistantId);

    /**
     * 根据日期范围查询
     */
    List<InvocationStat> findByStatDateBetween(String startDate, String endDate);

    /**
     * 根据助手ID查询（未删除）
     */
    @Query("SELECT s FROM InvocationStat s WHERE s.assistantId = :assistantId AND s.deleted = false ORDER BY s.statDate DESC")
    List<InvocationStat> findByAssistantId(@Param("assistantId") String assistantId);

    /**
     * 根据用户ID查询（未删除）
     */
    @Query("SELECT s FROM InvocationStat s WHERE s.userId = :userId AND s.deleted = false ORDER BY s.statDate DESC")
    List<InvocationStat> findByUserId(@Param("userId") String userId);

    /**
     * 统计助手总调用次数
     */
    @Query("SELECT SUM(s.invokeCount) FROM InvocationStat s WHERE s.assistantId = :assistantId AND s.deleted = false")
    Long sumInvokeCountByAssistantId(@Param("assistantId") String assistantId);

    /**
     * 统计助手总Token消耗
     */
    @Query("SELECT SUM(s.totalTokens) FROM InvocationStat s WHERE s.assistantId = :assistantId AND s.deleted = false")
    Long sumTotalTokensByAssistantId(@Param("assistantId") String assistantId);

    /**
     * 统计用户总调用次数
     */
    @Query("SELECT SUM(s.invokeCount) FROM InvocationStat s WHERE s.userId = :userId AND s.deleted = false")
    Long sumInvokeCountByUserId(@Param("userId") String userId);

    /**
     * 统计某日期的总调用次数
     */
    @Query("SELECT SUM(s.invokeCount) FROM InvocationStat s WHERE s.statDate = :statDate AND s.deleted = false")
    Long sumInvokeCountByDate(@Param("statDate") String statDate);

    /**
     * 统计某日期某助手的调用次数
     */
    @Query("SELECT SUM(s.invokeCount) FROM InvocationStat s WHERE s.statDate = :statDate AND s.assistantId = :assistantId AND s.deleted = false")
    Long sumInvokeCountByDateAndAssistantId(@Param("statDate") String statDate, @Param("assistantId") String assistantId);

    /**
     * 查询最近N天的统计数据
     */
    @Query("SELECT s FROM InvocationStat s WHERE s.statDate >= :startDate AND s.deleted = false ORDER BY s.statDate DESC")
    List<InvocationStat> findRecentDays(@Param("startDate") String startDate);
}

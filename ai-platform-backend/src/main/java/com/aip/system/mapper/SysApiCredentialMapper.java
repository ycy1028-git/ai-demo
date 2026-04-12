package com.aip.system.mapper;

import com.aip.system.entity.SysApiCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * API凭证Mapper
 */
@Repository
public interface SysApiCredentialMapper extends JpaRepository<SysApiCredential, String> {

    /**
     * 根据API Key查询（未删除）
     */
    @Query("SELECT c FROM SysApiCredential c WHERE c.apiKey = :apiKey AND c.deleted = false")
    Optional<SysApiCredential> findActiveByApiKey(@Param("apiKey") String apiKey);

    /**
     * 根据AppId查询（未删除）
     */
    @Query("SELECT c FROM SysApiCredential c WHERE c.appId = :appId AND c.deleted = false")
    Optional<SysApiCredential> findActiveByAppId(@Param("appId") String appId);

    /**
     * 根据关键词查询（模糊匹配名称、AppId、API Key）
     */
    @Query("SELECT c FROM SysApiCredential c WHERE c.deleted = false " +
           "AND (:keyword IS NULL OR :keyword = '' OR c.name LIKE %:keyword% " +
           "OR c.appId LIKE %:keyword% OR c.apiKey LIKE %:keyword%)")
    org.springframework.data.domain.Page<SysApiCredential> findByKeyword(
            @Param("keyword") String keyword,
            org.springframework.data.domain.Pageable pageable);

    /**
     * 查询所有启用的凭证
     */
    @Query("SELECT c FROM SysApiCredential c WHERE c.status = 1 AND c.deleted = false")
    List<SysApiCredential> findAllActive();

    /**
     * 查询未过期的凭证
     */
    @Query("SELECT c FROM SysApiCredential c WHERE c.status = 1 AND c.deleted = false " +
           "AND (c.expireTime IS NULL OR c.expireTime > :now)")
    List<SysApiCredential> findAllValid(@Param("now") Instant now);

    /**
     * 检查API Key是否存在
     */
    @Query("SELECT COUNT(c) > 0 FROM SysApiCredential c WHERE c.apiKey = :apiKey AND c.deleted = false")
    boolean existsByApiKey(@Param("apiKey") String apiKey);

    /**
     * 检查AppId是否存在
     */
    @Query("SELECT COUNT(c) > 0 FROM SysApiCredential c WHERE c.appId = :appId AND c.deleted = false")
    boolean existsByAppId(@Param("appId") String appId);

    /**
     * 增加调用次数
     */
    @Modifying
    @Query("UPDATE SysApiCredential c SET c.totalCalls = c.totalCalls + 1, " +
           "c.todayCalls = c.todayCalls + 1, c.monthlyCalls = c.monthlyCalls + 1, " +
           "c.lastCalledAt = :now WHERE c.id = :id")
    void incrementCalls(@Param("id") String id, @Param("now") Instant now);

    /**
     * 重置每日调用次数
     */
    @Modifying
    @Query("UPDATE SysApiCredential c SET c.todayCalls = 0, c.dailyResetDate = :date WHERE c.id = :id")
    void resetDailyCalls(@Param("id") String id, @Param("date") java.time.LocalDate date);

    /**
     * 重置每月调用次数
     */
    @Modifying
    @Query("UPDATE SysApiCredential c SET c.monthlyCalls = 0, c.monthlyResetDate = :month WHERE c.id = :id")
    void resetMonthlyCalls(@Param("id") String id, @Param("month") String month);
}

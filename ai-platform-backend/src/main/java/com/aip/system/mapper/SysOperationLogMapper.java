package com.aip.system.mapper;

import com.aip.system.entity.SysOperationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 操作日志Mapper
 */
@Repository
public interface SysOperationLogMapper extends JpaRepository<SysOperationLog, String> {

    /**
     * 根据用户ID查询
     */
    List<SysOperationLog> findByUserId(String userId);

    /**
     * 根据用户ID查询（分页）
     */
    Page<SysOperationLog> findByUserId(String userId, Pageable pageable);

    /**
     * 根据操作类型查询
     */
    List<SysOperationLog> findByOperationType(String operationType);

    /**
     * 根据对象类型查询
     */
    List<SysOperationLog> findByObjectType(String objectType);

    /**
     * 根据时间范围查询
     */
    @Query("SELECT l FROM SysOperationLog l WHERE l.createTime BETWEEN :startTime AND :endTime ORDER BY l.createTime DESC")
    List<SysOperationLog> findByTimeRange(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    /**
     * 查询近期的操作日志
     */
    @Query("SELECT l FROM SysOperationLog l ORDER BY l.createTime DESC")
    List<SysOperationLog> findRecent();

    /**
     * 根据用户ID删除（软删除）
     */
    @Query("UPDATE SysOperationLog l SET l.deleted = true WHERE l.userId = :userId")
    void softDeleteByUserId(@Param("userId") String userId);
}

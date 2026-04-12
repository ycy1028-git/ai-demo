package com.aip.app.mapper;

import com.aip.app.entity.ChatSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 对话会话Mapper
 */
@Repository
public interface ChatSessionMapper extends JpaRepository<ChatSession, String> {

    /**
     * 根据会话标识查询
     */
    Optional<ChatSession> findBySessionId(String sessionId);

    /**
     * 根据会话标识查询（未删除）
     */
    @Query("SELECT s FROM ChatSession s WHERE s.sessionId = :sessionId AND s.deleted = false")
    Optional<ChatSession> findActiveBySessionId(@Param("sessionId") String sessionId);

    /**
     * 根据用户ID查询会话（未删除）
     */
    @Query("SELECT s FROM ChatSession s WHERE s.userId = :userId AND s.deleted = false ORDER BY s.lastActiveAt DESC")
    List<ChatSession> findByUserId(@Param("userId") String userId);

    /**
     * 根据用户ID查询会话（分页，未删除）
     */
    @Query("SELECT s FROM ChatSession s WHERE s.userId = :userId AND s.deleted = false")
    Page<ChatSession> findByUserId(@Param("userId") String userId, Pageable pageable);

    /**
     * 根据助手ID查询会话（未删除）
     */
    @Query("SELECT s FROM ChatSession s WHERE s.assistantId = :assistantId AND s.deleted = false ORDER BY s.lastActiveAt DESC")
    List<ChatSession> findByAssistantId(@Param("assistantId") String assistantId);

    /**
     * 根据助手编码查询会话（未删除）
     */
    @Query("SELECT s FROM ChatSession s WHERE s.assistantCode = :assistantCode AND s.deleted = false ORDER BY s.lastActiveAt DESC")
    List<ChatSession> findByAssistantCode(@Param("assistantCode") String assistantCode);

    /**
     * 查询用户最近的会话
     */
    @Query("SELECT s FROM ChatSession s WHERE s.userId = :userId AND s.status = 1 AND s.deleted = false ORDER BY s.lastActiveAt DESC")
    List<ChatSession> findRecentByUserId(@Param("userId") String userId);

    /**
     * 查询进行中的会话（未删除）
     */
    @Query("SELECT s FROM ChatSession s WHERE s.status = 1 AND s.deleted = false ORDER BY s.lastActiveAt DESC")
    List<ChatSession> findAllActive();

    /**
     * 归档用户的所有会话
     */
    @Query("UPDATE ChatSession s SET s.status = 0, s.deleted = true WHERE s.userId = :userId")
    void archiveByUserId(@Param("userId") String userId);
}

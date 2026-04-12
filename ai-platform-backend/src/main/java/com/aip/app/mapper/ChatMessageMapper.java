package com.aip.app.mapper;

import com.aip.app.entity.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 对话消息Mapper
 */
@Repository
public interface ChatMessageMapper extends JpaRepository<ChatMessage, String> {

    /**
     * 根据会话ID查询消息（未删除）
     */
    @Query("SELECT m FROM ChatMessage m WHERE m.sessionId = :sessionId AND m.deleted = false ORDER BY m.createTime ASC")
    List<ChatMessage> findBySessionId(@Param("sessionId") String sessionId);

    /**
     * 根据会话ID查询消息（分页，未删除）
     */
    @Query("SELECT m FROM ChatMessage m WHERE m.sessionId = :sessionId AND m.deleted = false")
    Page<ChatMessage> findBySessionId(@Param("sessionId") String sessionId, Pageable pageable);

    /**
     * 根据会话ID查询最新消息
     */
    @Query("SELECT m FROM ChatMessage m WHERE m.sessionId = :sessionId AND m.deleted = false ORDER BY m.createTime DESC")
    List<ChatMessage> findRecentBySessionId(@Param("sessionId") String sessionId, Pageable pageable);

    /**
     * 根据角色查询会话消息
     */
    @Query("SELECT m FROM ChatMessage m WHERE m.sessionId = :sessionId AND m.role = :role AND m.deleted = false ORDER BY m.createTime ASC")
    List<ChatMessage> findBySessionIdAndRole(@Param("sessionId") String sessionId, @Param("role") String role);

    /**
     * 统计会话消息数量
     */
    @Query("SELECT COUNT(m) FROM ChatMessage m WHERE m.sessionId = :sessionId AND m.deleted = false")
    long countBySessionId(@Param("sessionId") String sessionId);

    /**
     * 根据会话ID删除（软删除）
     */
    @Query("UPDATE ChatMessage m SET m.deleted = true WHERE m.sessionId = :sessionId")
    void softDeleteBySessionId(@Param("sessionId") String sessionId);
}

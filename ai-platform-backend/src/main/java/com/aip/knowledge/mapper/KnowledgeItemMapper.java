package com.aip.knowledge.mapper;

import com.aip.knowledge.entity.KnowledgeItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 知识条目Mapper
 */
@Repository
public interface KnowledgeItemMapper extends JpaRepository<KnowledgeItem, String> {

    /**
     * 根据知识库ID查询（未删除）
     */
    @Query("SELECT k FROM KnowledgeItem k WHERE k.kbId = :kbId AND k.deleted = false ORDER BY k.createTime DESC")
    List<KnowledgeItem> findByKbId(@Param("kbId") String kbId);

    /**
     * 根据知识库ID查询（分页，未删除）
     */
    @Query("SELECT k FROM KnowledgeItem k WHERE k.kbId = :kbId AND k.deleted = false")
    Page<KnowledgeItem> findByKbId(@Param("kbId") String kbId, Pageable pageable);

    /**
     * 根据知识库ID查询已发布的条目
     */
    @Query("SELECT k FROM KnowledgeItem k WHERE k.kbId = :kbId AND k.status = 1 AND k.deleted = false ORDER BY k.createTime DESC")
    List<KnowledgeItem> findPublishedByKbId(@Param("kbId") String kbId);

    /**
     * 根据来源文档ID查询
     */
    @Query("SELECT k FROM KnowledgeItem k WHERE k.sourceDocId = :sourceDocId AND k.deleted = false")
    List<KnowledgeItem> findBySourceDocId(@Param("sourceDocId") String sourceDocId);

    /**
     * 根据标题查询
     */
    Optional<KnowledgeItem> findByTitle(String title);

    /**
     * 根据标题查询（未删除）
     */
    @Query("SELECT k FROM KnowledgeItem k WHERE k.title = :title AND k.deleted = false")
    Optional<KnowledgeItem> findActiveByTitle(@Param("title") String title);

    /**
     * 查询待向量化的条目
     */
    @Query("SELECT k FROM KnowledgeItem k WHERE k.vectorStatus = 0 AND k.status = 1 AND k.deleted = false")
    List<KnowledgeItem> findPendingVectorize();

    /**
     * 查询向量化的条目
     */
    @Query("SELECT k FROM KnowledgeItem k WHERE k.vectorStatus = 2 AND k.deleted = false")
    List<KnowledgeItem> findVectorized();

    /**
     * 统计知识库的条目数量
     */
    @Query("SELECT COUNT(k) FROM KnowledgeItem k WHERE k.kbId = :kbId AND k.deleted = false")
    long countByKbId(@Param("kbId") String kbId);

    /**
     * 统计知识库的已发布条目数量
     */
    @Query("SELECT COUNT(k) FROM KnowledgeItem k WHERE k.kbId = :kbId AND k.status = 1 AND k.deleted = false")
    long countPublishedByKbId(@Param("kbId") String kbId);

    /**
     * 根据知识库ID删除（软删除）
     */
    @Query("UPDATE KnowledgeItem k SET k.deleted = true WHERE k.kbId = :kbId")
    void softDeleteByKbId(@Param("kbId") String kbId);
}

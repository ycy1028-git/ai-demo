package com.aip.knowledge.mapper;

import com.aip.knowledge.entity.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 文档Mapper
 */
@Repository
public interface DocumentMapper extends JpaRepository<Document, String> {

    /**
     * 根据知识库ID查询（未删除）
     */
    @Query("SELECT d FROM Document d WHERE d.kbId = :kbId AND d.deleted = false ORDER BY d.createTime DESC")
    List<Document> findByKbId(@Param("kbId") String kbId);

    /**
     * 根据知识库ID查询（分页，未删除）
     */
    @Query("SELECT d FROM Document d WHERE d.kbId = :kbId AND d.deleted = false")
    Page<Document> findByKbId(@Param("kbId") String kbId, Pageable pageable);

    /**
     * 根据MinIO路径查询
     */
    Optional<Document> findByMinioPath(String minioPath);

    /**
     * 根据MinIO路径查询（未删除）
     */
    @Query("SELECT d FROM Document d WHERE d.minioPath = :minioPath AND d.deleted = false")
    Optional<Document> findActiveByMinioPath(@Param("minioPath") String minioPath);

    /**
     * 查询待处理的文档
     */
    @Query("SELECT d FROM Document d WHERE d.extractStatus = 0 AND d.deleted = false")
    List<Document> findPendingExtract();

    /**
     * 查询提取失败的文档
     */
    @Query("SELECT d FROM Document d WHERE d.extractStatus = 3 AND d.deleted = false")
    List<Document> findFailedExtract();

    /**
     * 查询已完成的文档
     */
    @Query("SELECT d FROM Document d WHERE d.extractStatus = 2 AND d.deleted = false")
    List<Document> findCompletedExtract();

    /**
     * 统计知识库的文档数量
     */
    @Query("SELECT COUNT(d) FROM Document d WHERE d.kbId = :kbId AND d.deleted = false")
    long countByKbId(@Param("kbId") String kbId);

    /**
     * 根据知识库ID删除（软删除）
     */
    @Query("UPDATE Document d SET d.deleted = true WHERE d.kbId = :kbId")
    void softDeleteByKbId(@Param("kbId") String kbId);
}

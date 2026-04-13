package com.aip.knowledge.mapper;

import com.aip.knowledge.entity.KnowledgeBase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 知识库Mapper
 */
@Repository
public interface KnowledgeBaseMapper extends JpaRepository<KnowledgeBase, String> {

    /**
     * 根据编码查询
     */
    Optional<KnowledgeBase> findByCode(String code);

    /**
     * 根据编码查询（未删除）
     */
    @Query("SELECT k FROM KnowledgeBase k WHERE k.code = :code AND k.deleted = false")
    Optional<KnowledgeBase> findActiveByCode(@Param("code") String code);

    /**
     * 根据ES索引名查询
     */
    Optional<KnowledgeBase> findByEsIndex(String esIndex);

    /**
     * 根据向量索引名查询
     */
    Optional<KnowledgeBase> findByVectorIndex(String vectorIndex);

    /**
     * 根据ES索引名查询（未删除）
     */
    @Query("SELECT k FROM KnowledgeBase k WHERE k.esIndex = :esIndex AND k.deleted = false")
    Optional<KnowledgeBase> findActiveByEsIndex(@Param("esIndex") String esIndex);

    /**
     * 查询所有启用的知识库（未删除）
     */
    @Query("SELECT k FROM KnowledgeBase k WHERE k.status = 1 AND k.deleted = false ORDER BY k.createTime DESC")
    List<KnowledgeBase> findAllActive();

    /**
     * 判断编码是否存在（未删除）
     */
    @Query("SELECT CASE WHEN COUNT(k) > 0 THEN true ELSE false END FROM KnowledgeBase k WHERE k.code = :code AND k.deleted = false")
    boolean existsByCode(@Param("code") String code);

    /**
     * 根据编码删除（软删除）
     */
    @Query("UPDATE KnowledgeBase k SET k.deleted = true WHERE k.code = :code")
    void softDeleteByCode(@Param("code") String code);
}

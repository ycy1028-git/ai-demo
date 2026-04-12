package com.aip.flow.mapper;

import com.aip.flow.entity.FlowTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 流程模板 Mapper
 */
@Repository
public interface FlowTemplateMapper extends JpaRepository<FlowTemplate, String> {

    /**
     * 查询所有启用的模板
     */
    List<FlowTemplate> findByStatusAndDeletedFalse(Integer status);

    /**
     * 根据模板编码查询
     */
    Optional<FlowTemplate> findByTemplateCodeAndDeletedFalse(String templateCode);

    /**
     * 查询所有启用的模板（按优先级降序）
     */
    @Query("SELECT t FROM FlowTemplate t WHERE t.status = 1 AND t.deleted = false ORDER BY t.priority DESC")
    List<FlowTemplate> findAllEnabledOrderByPriority();

    /**
     * 查询兜底模板
     */
    @Query("SELECT t FROM FlowTemplate t WHERE t.status = 1 AND t.deleted = false AND t.isFallback = 1")
    Optional<FlowTemplate> findFallbackTemplate();

    /**
     * 根据模板编码和状态查询
     */
    Optional<FlowTemplate> findByTemplateCodeAndStatusAndDeletedFalse(String templateCode, Integer status);

    /**
     * 检查编码是否存在（排除指定ID）
     */
    @Query("SELECT COUNT(t) > 0 FROM FlowTemplate t WHERE t.templateCode = :code AND t.id != :id AND t.deleted = false")
    boolean existsByTemplateCodeAndNotId(@Param("code") String templateCode, @Param("id") String id);
}

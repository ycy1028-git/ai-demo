package com.aip.ai.mapper;

import com.aip.ai.entity.AiModelConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * AI大模型配置Mapper
 */
@Repository
public interface AiModelConfigMapper extends JpaRepository<AiModelConfig, String> {

    /**
     * 查询启用的模型列表
     */
    @Query("SELECT m FROM AiModelConfig m WHERE m.enabled = true AND m.deleted = false ORDER BY m.sortOrder ASC")
    List<AiModelConfig> findAllEnabled();

    /**
     * 查询默认模型
     */
    @Query("SELECT m FROM AiModelConfig m WHERE m.isDefault = true AND m.enabled = true AND m.deleted = false")
    Optional<AiModelConfig> findDefault();

    /**
     * 根据模型名称查询
     */
    Optional<AiModelConfig> findByName(String name);

    /**
     * 根据提供商查询
     */
    @Query("SELECT m FROM AiModelConfig m WHERE m.provider = :provider AND m.enabled = true AND m.deleted = false")
    List<AiModelConfig> findByProvider(@Param("provider") String provider);

    /**
     * 判断编码是否存在（未删除）
     */
    @Query("SELECT CASE WHEN COUNT(m) > 0 THEN true ELSE false END FROM AiModelConfig m WHERE m.name = :name AND m.deleted = false")
    boolean existsByName(@Param("name") String name);

    /**
     * 取消所有默认标记
     */
    @Modifying
    @Query("UPDATE AiModelConfig m SET m.isDefault = false WHERE m.isDefault = true")
    void clearDefaultFlags();

    /**
     * 设置指定模型为默认
     */
    @Modifying
    @Query("UPDATE AiModelConfig m SET m.isDefault = true WHERE m.id = :id")
    void setAsDefault(@Param("id") String id);
}

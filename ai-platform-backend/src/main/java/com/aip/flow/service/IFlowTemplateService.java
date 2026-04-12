package com.aip.flow.service;

import com.aip.flow.entity.FlowTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

/**
 * 流程模板服务接口
 */
public interface IFlowTemplateService {

    /**
     * 分页查询模板
     */
    Page<FlowTemplate> findPage(String keyword, Integer status, Pageable pageable);

    /**
     * 获取所有启用的模板（按优先级排序）
     */
    List<FlowTemplate> findAllEnabled();

    /**
     * 根据ID查询
     */
    Optional<FlowTemplate> findById(String id);

    /**
     * 根据模板编码查询
     */
    Optional<FlowTemplate> findByCode(String templateCode);

    /**
     * 创建模板
     */
    FlowTemplate create(FlowTemplate template);

    /**
     * 更新模板
     */
    FlowTemplate update(FlowTemplate template);

    /**
     * 删除模板
     */
    void delete(String id);

    /**
     * 更新状态
     */
    void updateStatus(String id, Integer status);

    /**
     * 发布模板
     */
    void publish(String id);

    /**
     * 检查编码是否存在
     */
    boolean existsByCode(String code);

    /**
     * 检查编码是否存在（排除指定ID）
     */
    boolean existsByCodeAndNotId(String code, String id);
}

package com.aip.ai.service;

import com.aip.ai.dto.AiModelConfigDTO;
import com.aip.ai.dto.AiModelConfigVO;
import com.aip.ai.entity.AiModelConfig;
import com.aip.common.result.PageResult;
import com.aip.common.result.Result;

import java.util.List;

/**
 * AI大模型配置服务接口
 */
public interface IAiModelConfigService {

    /**
     * 获取所有模型配置列表
     */
    List<AiModelConfigVO> listAll();

    /**
     * 分页查询模型配置列表
     * @param name 模型名称（模糊搜索）
     * @param provider 提供商
     * @param enabled 启用状态
     * @param page 页码
     * @param size 每页数量
     * @return 分页结果
     */
    PageResult<AiModelConfigVO> listPage(String name, String provider, Boolean enabled, int page, int size);

    /**
     * 获取启用的模型列表
     */
    List<AiModelConfigVO> listEnabled();

    /**
     * 获取模型详情
     */
    AiModelConfigVO getById(String id);

    /**
     * 创建模型配置
     */
    Result<AiModelConfigVO> create(AiModelConfigDTO dto);

    /**
     * 更新模型配置
     */
    Result<AiModelConfigVO> update(String id, AiModelConfigDTO dto);

    /**
     * 删除模型配置
     */
    Result<Void> delete(String id);

    /**
     * 获取默认模型
     */
    AiModelConfig getDefaultModel();

    /**
     * 根据ID获取模型配置
     */
    AiModelConfig getModelById(String id);

    /**
     * 设置默认模型
     */
    Result<Void> setDefault(String id);

    /**
     * 测试模型连接
     */
    Result<String> testConnection(String id);

    /**
     * 获取所有模型ID（诊断用）
     */
    Result<String> getAllModelIds();
}

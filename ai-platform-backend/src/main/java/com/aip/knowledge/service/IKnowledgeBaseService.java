package com.aip.knowledge.service;

import com.aip.common.result.PageResult;
import com.aip.knowledge.dto.KnowledgeBaseDTO;
import com.aip.knowledge.entity.KnowledgeBase;

import java.util.List;
import java.util.Map;

/**
 * 知识库服务接口
 */
public interface IKnowledgeBaseService {

    /**
     * 分页查询知识库
     *
     * @param keyword 关键词搜索
     * @param type    类型筛选
     * @param status  状态筛选
     * @param page    页码
     * @param size    每页大小
     * @return 分页结果
     */
    PageResult<KnowledgeBase> page(String keyword, String type, Integer status, int page, int size);

    /**
     * 查询所有知识库
     *
     * @return 知识库列表
     */
    List<KnowledgeBase> list();

    /**
     * 根据ID查询知识库
     *
     * @param id 知识库ID（UUIDv7 无横杠字符串）
     * @return 知识库实体
     */
    KnowledgeBase getById(String id);

    /**
     * 根据编码查询知识库
     *
     * @param code 知识库编码
     * @return 知识库实体
     */
    KnowledgeBase getByCode(String code);

    /**
     * 根据编码获取知识库的ES索引名称
     *
     * @param code 知识库编码
     * @return ES索引名称
     */
    String getEsIndexByCode(String code);

    /**
     * 创建知识库
     *
     * @param dto 创建参数
     * @return 创建的知识库
     */
    KnowledgeBase create(KnowledgeBaseDTO dto);

    /**
     * 根据用户问题自动匹配知识库
     *
     * @param userQuestion 用户问题
     * @return 匹配的知识库列表
     */
    List<KnowledgeBase> matchByQuestion(String userQuestion);

    /**
     * 更新知识库
     *
     * @param id  知识库ID（UUIDv7 无横杠字符串）
     * @param dto 更新参数
     * @return 更新后的知识库
     */
    KnowledgeBase update(String id, KnowledgeBaseDTO dto);

    /**
     * 删除知识库
     *
     * @param id 知识库ID（UUIDv7 无横杠字符串）
     */
    void delete(String id);

    /**
     * 修改知识库状态
     *
     * @param id     知识库ID（UUIDv7 无横杠字符串）
     * @param status 状态值
     */
    void updateStatus(String id, Integer status);

    /**
     * 重建ES索引
     *
     * @param id 知识库ID（UUIDv7 无横杠字符串）
     */
    void rebuildIndex(String id);

    /**
     * 获取知识库统计信息
     *
     * @return 统计信息
     */
    Map<String, Object> getStatistics();
}

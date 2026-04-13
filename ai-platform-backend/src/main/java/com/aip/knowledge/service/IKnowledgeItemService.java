package com.aip.knowledge.service;

import com.aip.common.result.PageResult;
import com.aip.knowledge.dto.KnowledgeItemDTO;
import com.aip.knowledge.entity.KnowledgeBase;
import com.aip.knowledge.entity.KnowledgeItem;

import java.util.List;
import java.util.Map;

/**
 * 知识条目服务接口
 */
public interface IKnowledgeItemService {

    /**
     * 分页查询知识条目
     *
     * @param kbId    知识库ID（UUIDv7 无横杠字符串）
     * @param keyword 关键词
     * @param status  状态
     * @param page    页码
     * @param size    每页大小
     * @return 分页结果
     */
    PageResult<KnowledgeItem> page(String kbId, String keyword, Integer status, int page, int size);

    /**
     * 查询指定知识库的所有知识条目
     *
     * @param kbId 知识库ID（UUIDv7 无横杠字符串）
     * @return 知识条目列表
     */
    List<KnowledgeItem> listByKbId(String kbId);

    /**
     * 根据ID查询知识条目
     *
     * @param id 知识条目ID（UUIDv7 无横杠字符串）
     * @return 知识条目实体
     */
    KnowledgeItem getById(String id);

    /**
     * 创建知识条目
     *
     * @param dto 创建参数
     * @return 创建的知识条目
     */
    KnowledgeItem create(KnowledgeItemDTO dto);

    /**
     * 更新知识条目
     *
     * @param id  知识条目ID（UUIDv7 无横杠字符串）
     * @param dto 更新参数
     * @return 更新后的知识条目
     */
    KnowledgeItem update(String id, KnowledgeItemDTO dto);

    /**
     * 删除知识条目
     *
     * @param id 知识条目ID（UUIDv7 无横杠字符串）
     */
    void delete(String id);

    /**
     * 批量删除知识条目
     *
     * @param ids ID列表
     */
    void batchDelete(List<String> ids);

    /**
     * 修改知识条目状态
     *
     * @param id     知识条目ID（UUIDv7 无横杠字符串）
     * @param status 状态值
     */
    void updateStatus(String id, Integer status);

    /**
     * 向量化知识条目
     *
     * @param item         知识条目
     * @param knowledgeBase 知识库
     */
    void vectorizeItem(KnowledgeItem item, KnowledgeBase knowledgeBase);

    /**
     * 重新向量化指定条目
     *
     * @param id 知识条目ID（UUIDv7 无横杠字符串）
     */
    void revectorize(String id);

    /**
     * 批量向量化
     *
     * @param kbId 知识库ID（UUIDv7 无横杠字符串）
     * @return 向量化结果统计信息
     */
    Map<String, Object> vectorizeAll(String kbId);

    /**
     * 获取向量化状态统计
     *
     * @param kbId 知识库ID（UUIDv7 无横杠字符串）
     * @return 统计信息
     */
    Map<String, Object> getVectorizationStats(String kbId);

    /**
     * 解析标签JSON
     *
     * @param tagsJson 标签JSON
     * @return 标签列表
     */
    List<String> parseTags(String tagsJson);
}

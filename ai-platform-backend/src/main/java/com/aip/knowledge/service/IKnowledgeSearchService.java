package com.aip.knowledge.service;

import com.aip.knowledge.dto.KnowledgeDetailDTO;
import com.aip.knowledge.dto.KnowledgeSearchQueryDTO;
import com.aip.knowledge.dto.KnowledgeSearchResultDTO;
import com.aip.knowledge.dto.PageResultDTO;

import java.util.List;
import java.util.Map;

/**
 * 知识检索服务接口
 */
public interface IKnowledgeSearchService {

    /**
     * 知识检索
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResultDTO<KnowledgeSearchResultDTO> search(KnowledgeSearchQueryDTO query);

    /**
     * 获取知识详情
     *
     * @param itemId 知识条目ID
     * @return 知识详情
     */
    KnowledgeDetailDTO getDetail(String itemId);

    /**
     * 获取文档预览URL
     *
     * @param docId 文档ID
     * @return 预览URL
     */
    String getDocumentPreviewUrl(String docId);

    /**
     * 获取文档下载URL
     *
     * @param docId 文档ID
     * @return 下载URL
     */
    String getDocumentDownloadUrl(String docId);

    /**
     * 获取所有可用的知识库列表（供前端选择）
     *
     * @return 知识库列表
     */
    List<KnowledgeBaseSimpleDTO> getAvailableKnowledgeBases();

    /**
     * 获取调用次数统计（趋势数据）
     *
     * @param days 查询天数
     * @return 趋势数据列表 [{date: "2026-04-01", count: 100}, ...]
     */
    List<Map<String, Object>> getSearchStatistics(Integer days);

    /**
     * 知识库简要信息DTO
     */
    class KnowledgeBaseSimpleDTO {
        private String id;
        private String name;
        private String code;
        private String description;
        private Integer itemCount;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public Integer getItemCount() { return itemCount; }
        public void setItemCount(Integer itemCount) { this.itemCount = itemCount; }
    }
}

package com.aip.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识检索请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeSearchQueryDTO {

    /** 知识库ID（可选，不传则搜索所有知识库） */
    private String kbId;

    /** 搜索关键词 */
    @NotBlank(message = "搜索关键词不能为空")
    private String keyword;

    /** 搜索类型：keyword-关键词搜索, vector-向量搜索, hybrid-混合搜索 */
    private String searchType = "hybrid";

    /** 返回结果数 */
    private Integer topK = 10;

    /** 页码（从1开始） */
    private Integer page = 1;

    /** 每页大小 */
    private Integer pageSize = 10;
}

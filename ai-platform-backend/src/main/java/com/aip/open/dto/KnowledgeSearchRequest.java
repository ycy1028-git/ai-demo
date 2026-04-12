package com.aip.open.dto;

import lombok.Data;

/**
 * 开放API - 知识库检索请求
 */
@Data
public class KnowledgeSearchRequest {

    /**
     * 检索query
     */
    private String query;

    /**
     * 返回数量
     */
    private Integer topK = 5;

    /**
     * 相似度阈值（0-1）
     */
    private Double threshold = 0.7;

    /**
     * 知识库ID（可选，不传则搜索所有知识库）
     */
    private String knowledgeBaseId;
}

package com.aip.open.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 开放API - 知识库检索响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeSearchResponse {

    /**
     * 查询结果列表
     */
    private List<KnowledgeResult> results;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KnowledgeResult {
        /**
         * 知识ID
         */
        private String id;

        /**
         * 知识标题
         */
        private String title;

        /**
         * 知识内容摘要
         */
        private String snippet;

        /**
         * 相似度分数
         */
        private Double score;

        /**
         * 来源知识库
         */
        private String source;
    }
}

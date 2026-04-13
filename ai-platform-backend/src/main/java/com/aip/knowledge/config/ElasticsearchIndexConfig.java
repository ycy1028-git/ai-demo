package com.aip.knowledge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Elasticsearch索引配置属性
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "elasticsearch")
public class ElasticsearchIndexConfig {

    /** 知识库索引前缀 */
    private String indexPrefix = "kb_";

    /** 向量维度 */
    private Integer vectorDimension = 1024;

    /** 索引分片数 */
    private Integer shards = 1;

    /** 索引副本数 */
    private Integer replicas = 0;

    /**
     * 文本字段使用的分析器
     */
    private String textAnalyzer = "standard";

    public String generateIndexName(String kbCode) {
        return indexPrefix + kbCode.toLowerCase();
    }
}

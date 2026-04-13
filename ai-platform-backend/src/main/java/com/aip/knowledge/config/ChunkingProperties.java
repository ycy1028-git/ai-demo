package com.aip.knowledge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 知识分块配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "kb.chunking")
public class ChunkingProperties {

    /**
     * 每个chunk的最大token数
     */
    private int maxTokens = 450;

    /**
     * 最小token数，不足时尝试与后续内容合并
     */
    private int minTokens = 120;

    /**
     * chunk之间保留的重叠token数
     */
    private int overlapTokens = 60;

    /**
     * 是否在chunk中带上摘要
     */
    private boolean includeSummary = true;

    /**
     * 是否在chunk中带上标签
     */
    private boolean includeTags = true;

    /**
     * 是否带上文档元数据（文件名/类型等）
     */
    private boolean includeDocumentMetadata = true;
}

package com.aip.knowledge.service.chunk;

import java.util.List;

/**
 * 分块后的文本结果
 */
public record TextChunk(
        String text,
        ChunkType primaryType,
        List<String> anchors,
        List<ChunkType> segmentTypes
) {
}

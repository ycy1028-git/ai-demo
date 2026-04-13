package com.aip.knowledge.service.chunk;

import com.aip.knowledge.config.ChunkingProperties;
import com.aip.knowledge.entity.KnowledgeItem;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.text.BreakIterator;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 结构化内容分块器：支持标题/列表/表格识别，按token窗口切分并添加重叠窗口
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StructuredContentChunker {

    private static final Pattern MARKDOWN_HEADING = Pattern.compile("^(#{1,6})\\s*(.+)$");
    private static final Pattern NUMBER_HEADING = Pattern.compile("^(?:\\d+|[一二三四五六七八九十]+)[\\.、]\\s*(.+)$");
    private static final Pattern LIST_PATTERN = Pattern.compile("(?i)^(?:[-*+•·]|\\d+[\\.、)]|[a-z]\\.|[ivxlcdm]+\\.|[a-z]\\))\\s+.+$");
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");

    private final ChunkingProperties properties;
    private final ObjectMapper objectMapper;

    public List<TextChunk> chunk(KnowledgeItem item) {
        if (item == null) {
            return Collections.emptyList();
        }

        List<TextSegment> segments = new ArrayList<>();
        addMetadataSegments(item, segments);
        segments.addAll(extractContentSegments(item));

        if (segments.isEmpty()) {
            String fallback = StringUtils.hasText(item.getContent()) ? item.getContent() : item.getTitle();
            if (!StringUtils.hasText(fallback)) {
                return Collections.emptyList();
            }
            TextSegment fallbackSegment = new TextSegment(fallback, ChunkType.PARAGRAPH, List.of());
            return assembleChunks(List.of(fallbackSegment));
        }

        return assembleChunks(segments);
    }

    private void addMetadataSegments(KnowledgeItem item, List<TextSegment> segments) {
        if (StringUtils.hasText(item.getTitle())) {
            segments.add(new TextSegment(item.getTitle().trim(), ChunkType.TITLE, List.of(item.getTitle().trim())));
        }

        if (properties.isIncludeSummary() && StringUtils.hasText(item.getSummary())) {
            segments.add(new TextSegment("摘要：" + item.getSummary().trim(), ChunkType.SUMMARY, List.of()));
        }

        if (properties.isIncludeTags() && StringUtils.hasText(item.getTags())) {
            try {
                List<String> tags = objectMapper.readValue(item.getTags(), new TypeReference<List<String>>() {
                });
                if (!tags.isEmpty()) {
                    segments.add(new TextSegment("标签：" + String.join("、", tags), ChunkType.METADATA, List.of()));
                }
            } catch (Exception e) {
                log.debug("解析标签失败: {}", item.getTags(), e);
            }
        }

        if (properties.isIncludeDocumentMetadata() && StringUtils.hasText(item.getOriginalFileName())) {
            StringBuilder metadata = new StringBuilder("来源文件：").append(item.getOriginalFileName());
            if (StringUtils.hasText(item.getFileType())) {
                metadata.append("（类型：").append(item.getFileType()).append("）");
            }
            segments.add(new TextSegment(metadata.toString(), ChunkType.METADATA, List.of()));
        }
    }

    private List<TextSegment> extractContentSegments(KnowledgeItem item) {
        if (!StringUtils.hasText(item.getContent())) {
            return Collections.emptyList();
        }

        String normalized = normalizeContent(item.getContent());
        String[] lines = normalized.split("\\n");

        List<TextSegment> segments = new ArrayList<>();
        Deque<HeadingNode> headingStack = new ArrayDeque<>();
        StringBuilder paragraph = new StringBuilder();
        StringBuilder listBuffer = new StringBuilder();
        StringBuilder tableBuffer = new StringBuilder();

        for (String rawLine : lines) {
            String line = rawLine.strip();
            if (line.isEmpty()) {
                flushParagraph(segments, headingStack, paragraph);
                flushList(segments, headingStack, listBuffer);
                flushTable(segments, headingStack, tableBuffer);
                continue;
            }

            HeadingNode heading = parseHeading(line);
            if (heading != null) {
                flushParagraph(segments, headingStack, paragraph);
                flushList(segments, headingStack, listBuffer);
                flushTable(segments, headingStack, tableBuffer);
                pushHeading(headingStack, heading);
                segments.add(new TextSegment(heading.title, ChunkType.HEADING, currentAnchors(headingStack)));
                continue;
            }

            if (isListLine(line)) {
                flushParagraph(segments, headingStack, paragraph);
                flushTable(segments, headingStack, tableBuffer);
                if (listBuffer.length() > 0) {
                    listBuffer.append('\n');
                }
                listBuffer.append(line);
                continue;
            }

            if (looksLikeTable(line)) {
                flushParagraph(segments, headingStack, paragraph);
                flushList(segments, headingStack, listBuffer);
                if (tableBuffer.length() > 0) {
                    tableBuffer.append('\n');
                }
                tableBuffer.append(line);
                continue;
            }

            flushList(segments, headingStack, listBuffer);
            flushTable(segments, headingStack, tableBuffer);

            if (paragraph.length() > 0) {
                paragraph.append('\n');
            }
            paragraph.append(line);
        }

        flushParagraph(segments, headingStack, paragraph);
        flushList(segments, headingStack, listBuffer);
        flushTable(segments, headingStack, tableBuffer);
        return segments;
    }

    private void flushParagraph(List<TextSegment> segments, Deque<HeadingNode> headingStack, StringBuilder paragraph) {
        if (paragraph.length() == 0) {
            return;
        }
        segments.add(new TextSegment(paragraph.toString(), ChunkType.PARAGRAPH, currentAnchors(headingStack)));
        paragraph.setLength(0);
    }

    private void flushList(List<TextSegment> segments, Deque<HeadingNode> headingStack, StringBuilder listBuffer) {
        if (listBuffer.length() == 0) {
            return;
        }
        segments.add(new TextSegment(listBuffer.toString(), ChunkType.LIST, currentAnchors(headingStack)));
        listBuffer.setLength(0);
    }

    private void flushTable(List<TextSegment> segments, Deque<HeadingNode> headingStack, StringBuilder tableBuffer) {
        if (tableBuffer.length() == 0) {
            return;
        }
        segments.add(new TextSegment(tableBuffer.toString(), ChunkType.TABLE, currentAnchors(headingStack)));
        tableBuffer.setLength(0);
    }

    private HeadingNode parseHeading(String line) {
        Matcher markdown = MARKDOWN_HEADING.matcher(line);
        if (markdown.matches()) {
            return new HeadingNode(markdown.group(1).length(), markdown.group(2).trim());
        }
        Matcher numeric = NUMBER_HEADING.matcher(line);
        if (numeric.matches()) {
            return new HeadingNode(2, numeric.group(1).trim());
        }
        return null;
    }

    private void pushHeading(Deque<HeadingNode> stack, HeadingNode node) {
        while (!stack.isEmpty() && stack.peekLast().level >= node.level) {
            stack.removeLast();
        }
        stack.addLast(node);
    }

    private List<String> currentAnchors(Deque<HeadingNode> stack) {
        if (stack.isEmpty()) {
            return List.of();
        }
        List<String> anchors = new ArrayList<>();
        for (HeadingNode node : stack) {
            anchors.add(node.title);
        }
        return anchors;
    }

    private boolean isListLine(String line) {
        return LIST_PATTERN.matcher(line).matches();
    }

    private boolean looksLikeTable(String line) {
        return line.contains("|") || line.contains("\t");
    }

    private String normalizeContent(String content) {
        String normalized = content.replace("\r\n", "\n");
        normalized = normalized.replace("\r", "\n");
        normalized = normalized.replaceAll("(?i)<br\\s*/?>", "\n");
        normalized = normalized.replaceAll("(?i)</p>", "\n\n");
        normalized = normalized.replaceAll("(?i)</h[1-6]>", "\n");
        normalized = normalized.replaceAll("(?i)<li[^>]*>", "\n- ");
        normalized = normalized.replaceAll("(?i)</li>", "\n");
        normalized = normalized.replaceAll("(?i)</tr>", "\n");
        normalized = normalized.replaceAll("(?i)</td>", " | ");
        normalized = HTML_TAG_PATTERN.matcher(normalized).replaceAll(" ");
        normalized = normalized.replace('\u00A0', ' ');
        normalized = normalized.replaceAll("\n{3,}", "\n\n");
        return normalized;
    }

    private List<TextChunk> assembleChunks(List<TextSegment> segments) {
        List<TextChunk> chunks = new ArrayList<>();
        List<SegmentSlice> buffer = new ArrayList<>();
        Set<ChunkType> bufferTypes = new LinkedHashSet<>();
        List<String> activeAnchors = new ArrayList<>();
        int tokenCount = 0;

        for (TextSegment segment : segments) {
            if (!StringUtils.hasText(segment.text) || segment.tokens == 0) {
                continue;
            }

            if (segment.tokens > properties.getMaxTokens()) {
                for (TextSegment splitted : splitSegment(segment)) {
                    tokenCount = appendSegmentWithFlush(chunks, buffer, bufferTypes, activeAnchors, splitted, tokenCount);
                }
                continue;
            }

            tokenCount = appendSegmentWithFlush(chunks, buffer, bufferTypes, activeAnchors, segment, tokenCount);
        }

        if (!buffer.isEmpty()) {
            flushBuffer(chunks, buffer, bufferTypes, activeAnchors);
        }

        return chunks;
    }

    private int appendSegmentWithFlush(List<TextChunk> chunks,
                                       List<SegmentSlice> buffer,
                                       Set<ChunkType> bufferTypes,
                                       List<String> activeAnchors,
                                       TextSegment segment,
                                       int tokenCount) {
        if (!buffer.isEmpty() && tokenCount + segment.tokens > properties.getMaxTokens()) {
            tokenCount = flushBuffer(chunks, buffer, bufferTypes, activeAnchors);
        }

        buffer.add(new SegmentSlice(segment.text, segment.type, segment.anchors, segment.tokens));
        bufferTypes.add(segment.type);
        if (!segment.anchors.isEmpty()) {
            activeAnchors.clear();
            activeAnchors.addAll(segment.anchors);
        }
        return tokenCount + segment.tokens;
    }

    private int flushBuffer(List<TextChunk> chunks,
                            List<SegmentSlice> buffer,
                            Set<ChunkType> bufferTypes,
                            List<String> activeAnchors) {
        if (buffer.isEmpty()) {
            return 0;
        }

        StringBuilder sb = new StringBuilder();
        for (SegmentSlice slice : buffer) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(slice.text().trim());
        }
        String chunkText = sb.toString().trim();
        if (!StringUtils.hasText(chunkText)) {
            buffer.clear();
            bufferTypes.clear();
            return 0;
        }

        ChunkType primary = buffer.stream()
                .map(SegmentSlice::type)
                .filter(type -> type != ChunkType.OVERLAP)
                .findFirst()
                .orElse(ChunkType.PARAGRAPH);
        List<ChunkType> types = new ArrayList<>(bufferTypes);
        chunks.add(new TextChunk(chunkText, primary, List.copyOf(activeAnchors), types));

        String overlapSeed = buildOverlapSeed(buffer);
        buffer.clear();
        bufferTypes.clear();
        int overlapTokenCount = 0;

        if (properties.getOverlapTokens() > 0 && StringUtils.hasText(overlapSeed)) {
            overlapTokenCount = countTokens(overlapSeed);
            buffer.add(new SegmentSlice(overlapSeed, ChunkType.OVERLAP, List.copyOf(activeAnchors), overlapTokenCount));
            bufferTypes.add(ChunkType.OVERLAP);
        }

        return overlapTokenCount;
    }

    private String buildOverlapSeed(List<SegmentSlice> buffer) {
        int tokensNeeded = properties.getOverlapTokens();
        if (tokensNeeded <= 0) {
            return "";
        }

        List<String> slices = new ArrayList<>();
        int remaining = tokensNeeded;
        for (int i = buffer.size() - 1; i >= 0 && remaining > 0; i--) {
            SegmentSlice slice = buffer.get(i);
            String text = slice.text();
            if (slice.tokens <= remaining) {
                slices.add(text);
                remaining -= slice.tokens;
            } else {
                slices.add(takeLastTokens(text, remaining));
                remaining = 0;
            }
        }

        if (slices.isEmpty()) {
            return "";
        }
        Collections.reverse(slices);
        return String.join("\n", slices).trim();
    }

    private List<TextSegment> splitSegment(TextSegment segment) {
        List<TextSegment> parts = new ArrayList<>();
        BreakIterator iterator = BreakIterator.getSentenceInstance(Locale.ROOT);
        iterator.setText(segment.text);
        int start = iterator.first();
        for (int end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next()) {
            String piece = segment.text.substring(start, end).trim();
            if (!StringUtils.hasText(piece)) {
                continue;
            }
            TextSegment child = new TextSegment(piece, segment.type, segment.anchors);
            if (child.tokens > properties.getMaxTokens()) {
                parts.addAll(forceSplit(child));
            } else {
                parts.add(child);
            }
        }

        if (parts.isEmpty()) {
            return forceSplit(segment);
        }
        return parts;
    }

    private List<TextSegment> forceSplit(TextSegment segment) {
        List<TextSegment> parts = new ArrayList<>();
        String text = segment.text;
        int maxChars = Math.max(80, properties.getMaxTokens() * 4);
        for (int i = 0; i < text.length(); i += maxChars) {
            int end = Math.min(i + maxChars, text.length());
            String slice = text.substring(i, end).trim();
            if (!slice.isEmpty()) {
                parts.add(new TextSegment(slice, segment.type, segment.anchors));
            }
        }
        return parts;
    }

    private String takeLastTokens(String text, int tokensNeeded) {
        if (!StringUtils.hasText(text) || tokensNeeded <= 0) {
            return "";
        }
        BreakIterator iterator = BreakIterator.getWordInstance(Locale.ROOT);
        iterator.setText(text);
        List<Integer> boundaries = new ArrayList<>();
        List<Integer> ends = new ArrayList<>();
        int start = iterator.first();
        for (int end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next()) {
            String token = text.substring(start, end);
            if (token.trim().isEmpty()) {
                continue;
            }
            boundaries.add(start);
            ends.add(end);
        }
        if (boundaries.isEmpty()) {
            return text;
        }
        int fromIndex = Math.max(0, boundaries.size() - tokensNeeded);
        int fromChar = boundaries.get(fromIndex);
        return text.substring(fromChar).trim();
    }

    private int countTokens(String text) {
        if (!StringUtils.hasText(text)) {
            return 0;
        }
        BreakIterator iterator = BreakIterator.getWordInstance(Locale.ROOT);
        iterator.setText(text);
        int count = 0;
        int start = iterator.first();
        for (int end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next()) {
            String token = text.substring(start, end);
            if (token.trim().isEmpty()) {
                continue;
            }
            count++;
        }
        return Math.max(1, count);
    }

    private record HeadingNode(int level, String title) {
    }

    private record SegmentSlice(String text, ChunkType type, List<String> anchors, int tokens) {
    }

    private class TextSegment {
        private final String text;
        private final ChunkType type;
        private final List<String> anchors;
        private final int tokens;

        private TextSegment(String text, ChunkType type, List<String> anchors) {
            this.text = text == null ? "" : text.trim();
            this.type = type;
            this.anchors = anchors == null || anchors.isEmpty() ? List.of() : List.copyOf(anchors);
            this.tokens = countTokens(this.text);
        }
    }
}

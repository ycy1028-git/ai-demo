package com.aip.knowledge.controller;

import com.aip.common.result.Result;
import com.aip.knowledge.dto.*;
import com.aip.knowledge.service.IKnowledgeSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 知识检索控制器
 * 提供知识库检索API，供前端智能应用使用
 */
@Tag(name = "知识检索", description = "知识库检索接口")
@RestController
@RequestMapping("/knowledge")
@RequiredArgsConstructor
public class KnowledgeSearchController {

    private final IKnowledgeSearchService knowledgeSearchService;

    /**
     * 知识检索
     * 支持关键词搜索、向量搜索、混合搜索
     */
    @Operation(summary = "知识检索", description = "支持关键词搜索、向量搜索、混合搜索")
    @PostMapping("/search")
    public Result<PageResultDTO<KnowledgeSearchResultDTO>> search(
            @RequestBody @Valid KnowledgeSearchQueryDTO query) {
        PageResultDTO<KnowledgeSearchResultDTO> result = knowledgeSearchService.search(query);
        return Result.ok(result);
    }

    /**
     * 获取知识详情
     */
    @Operation(summary = "获取知识详情")
    @GetMapping("/detail/{itemId}")
    public Result<KnowledgeDetailDTO> getDetail(
            @Parameter(description = "知识条目ID") @PathVariable String itemId) {
        KnowledgeDetailDTO detail = knowledgeSearchService.getDetail(itemId);
        return Result.ok(detail);
    }

    /**
     * 获取文档预览URL
     */
    @Operation(summary = "获取文档预览URL")
    @GetMapping("/document/preview/{docId}")
    public Result<String> getPreviewUrl(
            @Parameter(description = "文档ID") @PathVariable String docId) {
        String url = knowledgeSearchService.getDocumentPreviewUrl(docId);
        return Result.ok(url);
    }

    /**
     * 获取文档下载URL
     */
    @Operation(summary = "获取文档下载URL")
    @GetMapping("/document/download/{docId}")
    public Result<String> getDownloadUrl(
            @Parameter(description = "文档ID") @PathVariable String docId) {
        String url = knowledgeSearchService.getDocumentDownloadUrl(docId);
        return Result.ok(url);
    }

    /**
     * 获取可用的知识库列表（供前端选择）
     */
    @Operation(summary = "获取可用的知识库列表")
    @GetMapping("/bases")
    public Result<List<IKnowledgeSearchService.KnowledgeBaseSimpleDTO>> getKnowledgeBases() {
        List<IKnowledgeSearchService.KnowledgeBaseSimpleDTO> bases = knowledgeSearchService.getAvailableKnowledgeBases();
        return Result.ok(bases);
    }

    /**
     * 获取调用次数统计（趋势数据）
     */
    @Operation(summary = "获取调用次数统计", description = "获取最近N天的调用次数趋势数据")
    @GetMapping("/search/statistics")
    public Result<List<Map<String, Object>>> getSearchStatistics(
            @Parameter(description = "查询天数") @RequestParam(required = false, defaultValue = "7") Integer days) {
        List<Map<String, Object>> statistics = knowledgeSearchService.getSearchStatistics(days);
        return Result.ok(statistics);
    }
}

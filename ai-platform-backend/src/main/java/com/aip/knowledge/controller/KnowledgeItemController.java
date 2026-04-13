package com.aip.knowledge.controller;

import com.aip.common.result.Result;
import com.aip.knowledge.dto.KnowledgeItemDTO;
import com.aip.knowledge.entity.KnowledgeItem;
import com.aip.knowledge.service.IKnowledgeItemService;
import com.aip.knowledge.service.MinioFileService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识条目控制器
 */
@Slf4j
@RestController
@RequestMapping("/kb/knowledge-item")
public class KnowledgeItemController {

    @Autowired
    private IKnowledgeItemService knowledgeItemService;

    @Autowired
    private MinioFileService minioFileService;

    /**
     * 分页查询知识条目
     */
    @GetMapping
    public Result<?> page(
            @RequestParam(required = false) String kbId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(knowledgeItemService.page(kbId, keyword, status, page, size));
    }

    /**
     * 查询指定知识库的所有知识条目
     */
    @GetMapping("/list")
    public Result<List<KnowledgeItem>> list(@RequestParam String kbId) {
        return Result.ok(knowledgeItemService.listByKbId(kbId));
    }

    /**
     * 根据ID查询
     */
    @GetMapping("/{id}")
    public Result<KnowledgeItem> getById(@PathVariable String id) {
        return Result.ok(knowledgeItemService.getById(id));
    }

    /**
     * 创建知识条目
     */
    @PostMapping
    public Result<KnowledgeItem> create(@Valid @RequestBody KnowledgeItemDTO dto) {
        return Result.ok(knowledgeItemService.create(dto));
    }

    /**
     * 更新知识条目
     */
    @PutMapping("/{id}")
    public Result<KnowledgeItem> update(@PathVariable String id, @Valid @RequestBody KnowledgeItemDTO dto) {
        return Result.ok(knowledgeItemService.update(id, dto));
    }

    /**
     * 删除知识条目
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        knowledgeItemService.delete(id);
        return Result.ok();
    }

    /**
     * 批量删除
     */
    @DeleteMapping("/batch")
    public Result<Void> batchDelete(@RequestBody List<String> ids) {
        knowledgeItemService.batchDelete(ids);
        return Result.ok();
    }

    /**
     * 修改状态
     */
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable String id, @RequestParam Integer status) {
        knowledgeItemService.updateStatus(id, status);
        return Result.ok();
    }

    /**
     * 重新向量化
     */
    @PostMapping("/{id}/vectorize")
    public Result<Void> vectorize(@PathVariable String id) {
        knowledgeItemService.revectorize(id);
        return Result.ok();
    }

    /**
     * 重建知识索引并生成向量
     */
    @PostMapping("/{id}/index")
    public Result<Void> index(@PathVariable String id) {
        knowledgeItemService.revectorize(id);
        return Result.ok();
    }

    /**
     * 批量向量化
     */
    @PostMapping("/vectorize-all")
    public Result<Map<String, Object>> vectorizeAll(@RequestParam String kbId) {
        return Result.ok(knowledgeItemService.vectorizeAll(kbId));
    }

    /**
     * 获取向量化统计
     */
    @GetMapping("/vectorization-stats")
    public Result<Map<String, Object>> getVectorizationStats(@RequestParam String kbId) {
        return Result.ok(knowledgeItemService.getVectorizationStats(kbId));
    }

    /**
     * 获取预览URL
     */
    @GetMapping("/{id}/preview")
    public Result<Map<String, String>> getPreviewUrl(@PathVariable String id) {
        KnowledgeItem item = knowledgeItemService.getById(id);
        if (item == null) {
            return Result.fail("知识条目不存在");
        }
        if (item.getMinioPath() == null || item.getMinioPath().isBlank()) {
            return Result.fail("该知识条目无关联文件");
        }

        String previewUrl = minioFileService.generatePreviewUrl(item.getMinioPath());
        String downloadUrl = minioFileService.generateDownloadUrl(
                item.getMinioPath(),
                item.getOriginalFileName() != null ? item.getOriginalFileName() : item.getTitle()
        );

        Map<String, String> result = new HashMap<>();
        result.put("previewUrl", previewUrl);
        result.put("downloadUrl", downloadUrl);
        result.put("fileName", item.getOriginalFileName());
        result.put("fileType", item.getFileType());

        return Result.ok(result);
    }

    /**
     * 下载文件
     */
    @GetMapping("/{id}/download")
    public Result<Map<String, String>> download(@PathVariable String id) {
        KnowledgeItem item = knowledgeItemService.getById(id);
        if (item == null) {
            return Result.fail("知识条目不存在");
        }
        if (item.getMinioPath() == null || item.getMinioPath().isBlank()) {
            return Result.fail("该知识条目无关联文件");
        }

        String downloadUrl = minioFileService.generateDownloadUrl(
                item.getMinioPath(),
                item.getOriginalFileName() != null ? item.getOriginalFileName() : item.getTitle()
        );

        Map<String, String> result = new HashMap<>();
        result.put("downloadUrl", downloadUrl);
        result.put("fileName", item.getOriginalFileName());
        result.put("fileType", item.getFileType());

        return Result.ok(result);
    }
}

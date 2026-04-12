package com.aip.knowledge.controller;

import com.aip.common.result.Result;
import com.aip.knowledge.dto.DocumentUploadDTO;
import com.aip.knowledge.entity.Document;
import com.aip.knowledge.entity.KnowledgeItem;
import com.aip.knowledge.service.IDocumentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文档控制器
 */
@Slf4j
@RestController
@RequestMapping("/kb/document")
public class DocumentController {

    @Autowired
    private IDocumentService documentService;

    /**
     * 上传文档
     */
    @PostMapping("/upload")
    public Result<Document> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("kbId") String kbId,
            @RequestParam(value = "name", required = false) String name) {
        return Result.ok(documentService.upload(file, kbId, name));
    }

    /**
     * 上传文档（DTO方式）
     */
    @PostMapping("/upload-with-dto")
    public Result<Document> uploadWithDto(
            @RequestParam("file") MultipartFile file,
            @RequestBody DocumentUploadDTO dto) {
        return Result.ok(documentService.upload(file, dto.getKbId(), dto.getName()));
    }

    /**
     * 上传文档并自动匹配知识库
     */
    @PostMapping("/upload-auto-match")
    public Result<java.util.Map<String, Object>> uploadAutoMatch(
            @RequestParam("file") MultipartFile file) {
        return Result.ok(documentService.uploadAndAutoMatch(file));
    }

    /**
     * 查询指定知识库的文档
     */
    @GetMapping("/list")
    public Result<List<Document>> list(@RequestParam String kbId) {
        return Result.ok(documentService.listByKbId(kbId));
    }

    /**
     * 根据ID查询
     */
    @GetMapping("/{id}")
    public Result<Document> getById(@PathVariable String id) {
        return Result.ok(documentService.getById(id));
    }

    /**
     * 删除文档
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        documentService.delete(id);
        return Result.ok();
    }

    /**
     * 获取文档下载URL
     */
    @GetMapping("/{id}/download-url")
    public Result<String> getDownloadUrl(@PathVariable String id) {
        return Result.ok(documentService.getDownloadUrl(id));
    }

    /**
     * 提取文档文本（Tika解析）
     */
    @PostMapping("/{id}/extract")
    public Result<String> extractText(@PathVariable String id) {
        return Result.ok(documentService.extractText(id));
    }

    /**
     * 从文档创建知识条目
     */
    @PostMapping("/{id}/create-knowledge-item")
    public Result<KnowledgeItem> createKnowledgeItem(@PathVariable String id) {
        return Result.ok(documentService.createKnowledgeItemFromDocument(id));
    }

    /**
     * 批量提取文档文本
     */
    @PostMapping("/batch-extract")
    public Result<Void> batchExtract(@RequestParam String kbId) {
        documentService.batchExtract(kbId);
        return Result.ok();
    }
}

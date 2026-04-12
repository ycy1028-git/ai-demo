package com.aip.knowledge.controller;

import com.aip.common.result.Result;
import com.aip.knowledge.dto.KnowledgeBaseDTO;
import com.aip.knowledge.entity.KnowledgeBase;
import com.aip.knowledge.service.IKnowledgeBaseService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 知识库控制器
 */
@Slf4j
@RestController
@RequestMapping("/kb/knowledge-base")
public class KnowledgeBaseController {

    @Autowired
    private IKnowledgeBaseService knowledgeBaseService;

    /**
     * 分页查询知识库
     */
    @GetMapping
    public Result<?> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(knowledgeBaseService.page(keyword, type, status, page, size));
    }

    /**
     * 查询所有知识库
     */
    @GetMapping("/list")
    public Result<List<KnowledgeBase>> list() {
        return Result.ok(knowledgeBaseService.list());
    }

    /**
     * 根据ID查询
     */
    @GetMapping("/{id}")
    public Result<KnowledgeBase> getById(@PathVariable String id) {
        return Result.ok(knowledgeBaseService.getById(id));
    }

    /**
     * 根据编码查询
     */
    @GetMapping("/code/{code}")
    public Result<KnowledgeBase> getByCode(@PathVariable String code) {
        return Result.ok(knowledgeBaseService.getByCode(code));
    }

    /**
     * 创建知识库
     */
    @PostMapping
    public Result<KnowledgeBase> create(@Valid @RequestBody KnowledgeBaseDTO dto) {
        return Result.ok(knowledgeBaseService.create(dto));
    }

    /**
     * 更新知识库
     */
    @PutMapping("/{id}")
    public Result<KnowledgeBase> update(@PathVariable String id, @Valid @RequestBody KnowledgeBaseDTO dto) {
        return Result.ok(knowledgeBaseService.update(id, dto));
    }

    /**
     * 删除知识库
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        knowledgeBaseService.delete(id);
        return Result.ok();
    }

    /**
     * 修改状态
     */
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable String id, @RequestParam Integer status) {
        knowledgeBaseService.updateStatus(id, status);
        return Result.ok();
    }

    /**
     * 重建ES索引
     */
    @PostMapping("/{id}/rebuild-index")
    public Result<Void> rebuildIndex(@PathVariable String id) {
        knowledgeBaseService.rebuildIndex(id);
        return Result.ok();
    }

    /**
     * 获取统计信息
     */
    @GetMapping("/statistics")
    public Result<Map<String, Object>> getStatistics() {
        return Result.ok(knowledgeBaseService.getStatistics());
    }
}

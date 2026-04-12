package com.aip.ai.controller;

import com.aip.ai.dto.AiModelConfigDTO;
import com.aip.ai.dto.AiModelConfigVO;
import com.aip.ai.service.IAiModelConfigService;
import com.aip.common.result.PageResult;
import com.aip.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AI大模型配置管理接口
 */
@Slf4j
@Tag(name = "AI模型配置", description = "AI大模型配置管理接口")
@RestController
@RequestMapping("/ai/models")
@RequiredArgsConstructor
public class AiModelConfigController {

    private final IAiModelConfigService aiModelConfigService;

    @Operation(summary = "获取模型配置列表（支持分页和搜索）")
    @GetMapping
    public Result<PageResult<AiModelConfigVO>> list(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(aiModelConfigService.listPage(name, provider, enabled, page, size));
    }

    @Operation(summary = "获取启用的模型列表")
    @GetMapping("/enabled")
    public Result<List<AiModelConfigVO>> listEnabled() {
        return Result.ok(aiModelConfigService.listEnabled());
    }

    @Operation(summary = "获取模型详情")
    @GetMapping("/{id}")
    public Result<AiModelConfigVO> getById(@PathVariable String id) {
        log.info("获取模型详情: id={}", id);
        return Result.ok(aiModelConfigService.getById(id));
    }

    @Operation(summary = "创建模型配置")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<AiModelConfigVO> create(@Valid @RequestBody AiModelConfigDTO dto) {
        return aiModelConfigService.create(dto);
    }

    @Operation(summary = "更新模型配置")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<AiModelConfigVO> update(@PathVariable String id, @Valid @RequestBody AiModelConfigDTO dto) {
        log.info("更新模型配置: id={}", id);
        return aiModelConfigService.update(id, dto);
    }

    @Operation(summary = "删除模型配置")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> delete(@PathVariable String id) {
        log.info("删除模型配置: id={}", id);
        return aiModelConfigService.delete(id);
    }

    @Operation(summary = "设置默认模型")
    @PutMapping("/{id}/default")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> setDefault(@PathVariable String id) {
        log.info("设置默认模型: id={}", id);
        return aiModelConfigService.setDefault(id);
    }

    @Operation(summary = "测试模型连接")
    @PostMapping("/{id}/test")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<String> testConnection(@PathVariable String id) {
        log.info("测试模型连接: id={}", id);
        return aiModelConfigService.testConnection(id);
    }

    @Operation(summary = "获取所有模型ID（诊断用）")
    @GetMapping("/debug/all-ids")
    public Result<String> getAllModelIds() {
        return aiModelConfigService.getAllModelIds();
    }
}

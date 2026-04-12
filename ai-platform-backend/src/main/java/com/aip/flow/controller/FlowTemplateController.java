package com.aip.flow.controller;

import com.aip.common.result.Result;
import com.aip.flow.entity.FlowTemplate;
import com.aip.flow.service.IFlowTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 流程模板管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/flow/template")
@RequiredArgsConstructor
public class FlowTemplateController {

    private final IFlowTemplateService flowTemplateService;

    @GetMapping
    public Result<Page<FlowTemplate>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {

        Page<FlowTemplate> pageResult = flowTemplateService.findPage(
                keyword, status, PageRequest.of(page - 1, pageSize));
        return Result.ok(pageResult);
    }

    @GetMapping("/all")
    public Result<List<FlowTemplate>> listAll() {
        return Result.ok(flowTemplateService.findAllEnabled());
    }

    @GetMapping("/{id}")
    public Result<FlowTemplate> getById(@PathVariable String id) {
        return flowTemplateService.findById(id)
                .map(Result::ok)
                .orElse(Result.fail("模板不存在"));
    }

    @GetMapping("/code/{code}")
    public Result<FlowTemplate> getByCode(@PathVariable String code) {
        return flowTemplateService.findByCode(code)
                .map(Result::ok)
                .orElse(Result.fail("模板不存在"));
    }

    @PostMapping
    public Result<FlowTemplate> create(@RequestBody FlowTemplate template) {
        FlowTemplate created = flowTemplateService.create(template);
        return Result.ok(created);
    }

    @PutMapping
    public Result<FlowTemplate> update(@RequestBody FlowTemplate template) {
        FlowTemplate updated = flowTemplateService.update(template);
        return Result.ok(updated);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        flowTemplateService.delete(id);
        return Result.ok();
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(
            @PathVariable String id,
            @RequestParam Integer status) {
        flowTemplateService.updateStatus(id, status);
        return Result.ok();
    }

    @PostMapping("/{id}/publish")
    public Result<Void> publish(@PathVariable String id) {
        flowTemplateService.publish(id);
        return Result.ok();
    }
}

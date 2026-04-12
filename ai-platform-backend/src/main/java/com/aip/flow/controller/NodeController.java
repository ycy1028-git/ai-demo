package com.aip.flow.controller;

import com.aip.common.result.Result;
import com.aip.flow.dto.RegisteredNode;
import com.aip.flow.service.INodeRegistryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 节点定义控制器
 */
@Slf4j
@RestController
@RequestMapping("/flow/nodes")
@RequiredArgsConstructor
public class NodeController {

    private final INodeRegistryService nodeRegistryService;

    @GetMapping
    public Result<List<RegisteredNode>> getNodeDefinitions() {
        List<RegisteredNode> nodes = nodeRegistryService.getAllNodes();
        return Result.ok(nodes);
    }

    @GetMapping("/description")
    public Result<String> getNodeDescription() {
        String description = nodeRegistryService.generateNodeDescription();
        return Result.ok(description);
    }
}

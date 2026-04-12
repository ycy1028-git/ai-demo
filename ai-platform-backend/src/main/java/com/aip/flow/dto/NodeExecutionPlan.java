package com.aip.flow.dto;

import lombok.Data;
import lombok.Builder;
import java.util.List;

/**
 * 节点执行计划
 */
@Data
@Builder
public class NodeExecutionPlan {

    private String planId;
    private String strategy;
    private String reason;
    private double confidence;
    private List<PlannedNode> nodes;

    @Data
    @Builder
    public static class PlannedNode {
        private String nodeType;
        private String nodeName;
        private java.util.Map<String, Object> config;
        private int order;
        private String reason;
        private boolean required;
    }

    public boolean isEmpty() {
        return nodes == null || nodes.isEmpty();
    }
}

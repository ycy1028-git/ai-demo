package com.aip.flow.engine;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * 流程定义
 * 解析后的流程配置
 */
@Data
public class FlowDefinition {

    private String flowId;
    private String flowName;
    private List<FlowNode> nodes;

    @Data
    public static class FlowNode {
        private String id;
        private String type;
        private String name;
        private Map<String, Object> data;
        private List<String> nextNodes;
    }

    public FlowNode getNodeByIndex(int index) {
        if (nodes == null || index < 0 || index >= nodes.size()) {
            return null;
        }
        return nodes.get(index);
    }

    public int findNodeIndexById(String nodeId) {
        if (nodes == null || nodeId == null) {
            return -1;
        }
        for (int i = 0; i < nodes.size(); i++) {
            if (nodeId.equals(nodes.get(i).getId())) {
                return i;
            }
        }
        return -1;
    }
}

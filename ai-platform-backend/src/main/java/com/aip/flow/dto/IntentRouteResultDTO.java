package com.aip.flow.dto;

import lombok.Data;
import lombok.Builder;
import java.util.List;

/**
 * 意图路由结果 DTO
 */
@Data
@Builder
public class IntentRouteResultDTO {

    /** 路由类型 */
    private String routeType;

    /** 模板编码 */
    private String templateCode;

    /** 模板名称 */
    private String templateName;

    /** 意图 */
    private String intent;

    /** 置信度 */
    private Double confidence;

    /** 建议回复 */
    private String prompt;

    /** 是否需要更多输入 */
    private Boolean needMoreInput;

    /** 规划节点列表 */
    private List<PlannedNodeDTO> nodes;

    @Data
    @Builder
    public static class PlannedNodeDTO {
        private String nodeType;
        private String nodeName;
        private Integer order;
        private String reason;
        private Boolean required;
    }
}

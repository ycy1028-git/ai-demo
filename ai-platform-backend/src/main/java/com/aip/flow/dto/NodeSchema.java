package com.aip.flow.dto;

import lombok.Data;
import lombok.Builder;
import java.util.Map;

/**
 * 参数 Schema 定义
 * 用于描述节点的输入、输出、配置参数
 */
@Data
@Builder
public class NodeSchema {

    private String name;        // Schema 名称
    private String type;        // 参数类型（object/string/number/boolean）
    private String description; // 参数描述
    private boolean required;   // 是否必需
    private Map<String, Property> properties; // 对象类型的属性

    @Data
    @Builder
    public static class Property {
        private String type;
        private String description;
        private boolean required;
        private Object defaultValue;
        private java.util.List<String> enumValues; // 枚举值
        private Property items; // 数组类型的元素定义
    }
}

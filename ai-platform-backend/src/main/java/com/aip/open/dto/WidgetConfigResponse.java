package com.aip.open.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 开放API - 嵌入配置响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WidgetConfigResponse {

    /**
     * Widget Token
     */
    private String widgetToken;

    /**
     * WebSocket服务器地址
     */
    private String serverUrl;

    /**
     * 主题配置
     */
    private ThemeConfig theme;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ThemeConfig {
        private String primaryColor = "#1890ff";
        private String position = "right";
        private Integer zIndex = 9999;
        private Offset offset;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Offset {
        private Integer x = 20;
        private Integer y = 100;
    }
}

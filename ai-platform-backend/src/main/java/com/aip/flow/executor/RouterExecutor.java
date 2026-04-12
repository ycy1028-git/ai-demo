package com.aip.flow.executor;

import com.aip.flow.engine.FlowContext;
import com.aip.flow.engine.NodeResult;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 路由节点执行器
 * 根据用户消息的关键词，将流程路由到不同的处理分支
 * <p>
 * 使用场景：
 * - 简单关键词匹配：报销 → 报销流程，请假 → 请假流程
 * - 默认路由：当没有匹配时使用第一个路由作为默认
 */
@Slf4j
@Component
public class RouterExecutor extends BaseNodeExecutor {

    /**
     * 初始化基础属性
     * 注册为 "router" 节点，支持关键词匹配路由
     */
    @PostConstruct
    public void init() {
        initBase("router", "意图路由", "根据用户意图选择合适的处理流程或专家", "foundation",
                Arrays.asList("路由", "选择", "跳转"));
    }

    /**
     * 执行路由逻辑
     * <p>
     * 配置格式（通过 flowData 传入）：
     * [
     *   {"keyword": "报销", "targetFlow": "reimbursement_flow"},
     *   {"keyword": "请假", "targetFlow": "leave_flow"}
     * ]
     *
     * @param context 流程上下文，包含用户当前消息
     * @param config 路由配置，包含 routes 数组
     * @return 路由结果，包含 selected_route（选中的路由编码）和 route_config（路由配置）
     */
    @Override
    public NodeResult execute(FlowContext context, Map<String, Object> config) {
        try {
            // 获取路由配置列表
            List<Map<String, Object>> routes = (List<Map<String, Object>>) config.getOrDefault("routes", new ArrayList<>());

            // 获取当前用户消息
            String message = context.getCurrentMessage();
            String selectedRoute = null;
            Map<String, Object> routeConfig = null;

            // 遍历路由配置，匹配关键词
            for (Map<String, Object> route : routes) {
                String keyword = (String) route.getOrDefault("keyword", "");
                if (keyword.isBlank() || message.contains(keyword)) {
                    selectedRoute = (String) route.get("targetFlow");
                    routeConfig = route;
                    break;
                }
            }

            // 如果没有匹配到，使用第一个路由作为默认
            if (selectedRoute == null && !routes.isEmpty()) {
                routeConfig = routes.get(0);
                selectedRoute = (String) routeConfig.get("targetFlow");
            }

            // 构建结果参数
            Map<String, Object> params = new HashMap<>();
            params.put("selected_route", selectedRoute);
            params.put("route_config", routeConfig);

            // 设置下一步节点（如果配置了 targetFlow）
            if (selectedRoute != null) {
                params.put("_next_node", selectedRoute);
            }

            log.info("路由选择完成: selectedRoute={}, message={}", selectedRoute, message);
            return NodeResult.success("路由选择完成: " + selectedRoute, params);

        } catch (Exception e) {
            log.error("路由异常: {}", e.getMessage(), e);
            return NodeResult.fail("路由选择失败", "ROUTER_ERROR");
        }
    }
}
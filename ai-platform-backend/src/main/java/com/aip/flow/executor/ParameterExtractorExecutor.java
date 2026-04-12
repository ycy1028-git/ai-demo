package com.aip.flow.executor;

import com.aip.flow.engine.FlowContext;
import com.aip.flow.engine.NodeResult;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 参数提取节点执行器
 * 从用户输入中提取结构化参数
 */
@Slf4j
@Component
public class ParameterExtractorExecutor extends BaseNodeExecutor {

    @PostConstruct
    public void init() {
        initBase("parameter_extractor", "参数提取", 
                "从用户消息中提取结构化参数，如姓名、电话、日期等", "foundation",
                Arrays.asList("提取", "参数", "获取", "填写"));
    }

    @Override
    public NodeResult execute(FlowContext context, Map<String, Object> config) {
        try {
            List<Map<String, Object>> paramDefs = (List<Map<String, Object>>) config.getOrDefault("params", new ArrayList<>());

            String message = context.getCurrentMessage();
            Map<String, Object> extractedParams = new HashMap<>();

            for (Map<String, Object> paramDef : paramDefs) {
                String paramName = (String) paramDef.getOrDefault("name", "");
                String paramType = (String) paramDef.getOrDefault("type", "string");
                String pattern = (String) paramDef.getOrDefault("pattern", "");
                
                if (!pattern.isBlank()) {
                    Pattern p = Pattern.compile(pattern);
                    Matcher m = p.matcher(message);
                    if (m.find()) {
                        String value = m.group(1) != null ? m.group(1) : m.group();
                        extractedParams.put(paramName, convertValue(value, paramType));
                    }
                }
            }

            if (!extractedParams.isEmpty()) {
                context.getParams().putAll(extractedParams);
            }

            return NodeResult.success("参数提取完成，共提取 " + extractedParams.size() + " 个参数", 
                    new HashMap<>(extractedParams));

        } catch (Exception e) {
            log.error("参数提取异常: {}", e.getMessage(), e);
            return NodeResult.fail("参数提取失败", "EXTRACTOR_ERROR");
        }
    }

    private Object convertValue(String value, String type) {
        if (value == null) return null;
        
        return switch (type) {
            case "integer" -> Integer.parseInt(value.replaceAll("[^0-9]", ""));
            case "float", "double" -> Double.parseDouble(value.replaceAll("[^0-9.]", ""));
            case "date" -> value;
            case "phone" -> value.replaceAll("[^0-9-]", "");
            default -> value;
        };
    }
}
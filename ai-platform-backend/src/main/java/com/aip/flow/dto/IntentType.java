package com.aip.flow.dto;

/**
 * 意图类型枚举
 */
public enum IntentType {
    /**
     * 查询知识、政策、制度、手册等静态信息
     */
    KNOWLEDGE_QUERY("知识查询"),
    
    /**
     * 执行操作、提交申请、创建流程等动态操作
     */
    ACTION_EXECUTION("操作执行"),
    
    /**
     * 闲聊、问候、日常对话等
     */
    CONVERSATION("对话交流"),
    
    /**
     * 意图不明确或混合意图
     */
    AMBIGUOUS("意图模糊");

    private final String description;

    IntentType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}

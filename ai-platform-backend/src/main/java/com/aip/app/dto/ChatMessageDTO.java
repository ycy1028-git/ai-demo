package com.aip.app.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 对话消息DTO
 */
@Data
public class ChatMessageDTO {

    private Long id;

    /** 所属会话ID */
    @NotBlank(message = "会话ID不能为空")
    private String sessionId;

    /** 消息角色：user/assistant/system */
    @NotBlank(message = "消息角色不能为空")
    private String role;

    /** 消息内容 */
    @NotBlank(message = "消息内容不能为空")
    private String content;

    /** 关联的知识库ID列表 */
    private String relatedKbIds;

    /** 检索到的知识条目数量 */
    private Integer relatedCount;

    /** 引用来源 */
    private String citations;

    /** Token消耗 */
    private Integer tokens;

    /** 模型名称 */
    private String model;

    /** 响应耗时（毫秒） */
    private Long responseTime;

    /** 错误信息 */
    private String error;

    /** 是否成功 */
    private Integer success;
}

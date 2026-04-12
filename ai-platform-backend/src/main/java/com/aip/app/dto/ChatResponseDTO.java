package com.aip.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 聊天响应DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponseDTO {

    /** 会话ID */
    private String sessionId;

    /** 助手回复 */
    private String reply;

    /** 引用来源 */
    private String citations;

    /** 关联的知识条目数量 */
    private Integer relatedCount;

    /** Token消耗 */
    private Integer tokens;

    /** 响应时间（毫秒） */
    private Long responseTime;

    /** 是否成功 */
    private Boolean success;

    /** 错误信息 */
    private String error;
}

package com.aip.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统计查询DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatQueryDTO {

    /** 统计日期 */
    private String statDate;

    /** 开始日期 */
    private String startDate;

    /** 结束日期 */
    private String endDate;

    /** 助手ID（UUIDv7 无横杠字符串） */
    private String assistantId;

    /** 助手编码 */
    private String assistantCode;

    /** 用户ID（UUIDv7 无横杠字符串） */
    private String userId;
}

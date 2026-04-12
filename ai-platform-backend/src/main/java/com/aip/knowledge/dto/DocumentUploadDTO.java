package com.aip.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 文档上传DTO
 */
@Data
public class DocumentUploadDTO {

    /** 所属知识库ID（UUIDv7 无横杠字符串） */
    @NotBlank(message = "知识库ID不能为空")
    private String kbId;

    /** 文档名称（可选，默认使用原始文件名） */
    private String name;

    /** 文档描述 */
    private String description;
}

package com.aip.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 统一错误码枚举
 *
 * <p>错误码规范：
 * <ul>
 *   <li>1xxx - 系统级错误</li>
 *   <li>2xxx - 认证授权错误</li>
 *   <li>3xxx - 业务数据错误</li>
 *   <li>4xxx - 参数校验错误</li>
 *   <li>5xxx - 第三方服务错误</li>
 *   <li>9xxx - 自定义业务错误</li>
 * </ul>
 */
@Getter
@AllArgsConstructor
public enum ErrorCode {

    // ==================== 系统级错误 (1xxx) ====================
    SUCCESS(1000, "success", HttpStatus.OK),
    SYSTEM_ERROR(1001, "系统异常，请稍后重试", HttpStatus.INTERNAL_SERVER_ERROR),
    SERVICE_UNAVAILABLE(1002, "服务暂不可用", HttpStatus.SERVICE_UNAVAILABLE),
    DATA_PROCESSING_ERROR(1003, "数据处理异常", HttpStatus.INTERNAL_SERVER_ERROR),

    // ==================== 认证授权错误 (2xxx) ====================
    UNAUTHORIZED(2001, "未登录或登录已过期", HttpStatus.UNAUTHORIZED),
    TOKEN_INVALID(2002, "Token无效", HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED(2003, "Token已过期", HttpStatus.UNAUTHORIZED),
    TOKEN_MALFORMED(2004, "Token格式错误", HttpStatus.UNAUTHORIZED),
    FORBIDDEN(2003, "无访问权限", HttpStatus.FORBIDDEN),
    ACCESS_DENIED(2004, "访问被拒绝", HttpStatus.FORBIDDEN),

    // ==================== 业务数据错误 (3xxx) ====================

    // 通用 (30xx)
    NOT_FOUND(3000, "数据不存在", HttpStatus.NOT_FOUND),

    // 用户相关 (31xx)
    USER_NOT_FOUND(3001, "用户不存在", HttpStatus.NOT_FOUND),
    USER_ALREADY_EXISTS(3002, "用户已存在", HttpStatus.CONFLICT),
    USER_DISABLED(3003, "用户已被禁用", HttpStatus.FORBIDDEN),
    USER_PASSWORD_ERROR(3004, "用户名或密码错误", HttpStatus.UNAUTHORIZED),
    PASSWORD_INVALID(3005, "密码格式不正确", HttpStatus.BAD_REQUEST),
    PASSWORD_NOT_MATCH(3006, "两次输入的密码不一致", HttpStatus.BAD_REQUEST),
    OLD_PASSWORD_ERROR(3007, "原密码错误", HttpStatus.BAD_REQUEST),

    // AI助手相关 (31xx)
    ASSISTANT_NOT_FOUND(3101, "AI助手不存在", HttpStatus.NOT_FOUND),
    ASSISTANT_DISABLED(3102, "AI助手已禁用", HttpStatus.FORBIDDEN),
    ASSISTANT_CODE_EXISTS(3103, "助手编码已存在", HttpStatus.CONFLICT),

    // 对话相关 (32xx)
    SESSION_NOT_FOUND(3201, "会话不存在", HttpStatus.NOT_FOUND),
    SESSION_EXPIRED(3202, "会话已过期", HttpStatus.GONE),
    MESSAGE_NOT_FOUND(3203, "消息不存在", HttpStatus.NOT_FOUND),

    // 知识库相关 (33xx)
    KNOWLEDGE_BASE_NOT_FOUND(3301, "知识库不存在", HttpStatus.NOT_FOUND),
    KNOWLEDGE_BASE_DISABLED(3302, "知识库已禁用", HttpStatus.FORBIDDEN),
    KNOWLEDGE_BASE_CODE_EXISTS(3303, "知识库编码已存在", HttpStatus.CONFLICT),
    KNOWLEDGE_ITEM_NOT_FOUND(3401, "知识条目不存在", HttpStatus.NOT_FOUND),
    KNOWLEDGE_ITEM_PUBLISHED(3402, "知识条目已发布，无法修改", HttpStatus.CONFLICT),
    KNOWLEDGE_ITEM_VECTORIZING(3403, "知识条目正在向量化中", HttpStatus.CONFLICT),

    // 文档相关 (35xx)
    DOCUMENT_NOT_FOUND(3501, "文档不存在", HttpStatus.NOT_FOUND),
    DOCUMENT_EXTRACTING(3502, "文档正在提取中", HttpStatus.CONFLICT),
    DOCUMENT_EXTRACT_FAILED(3503, "文档提取失败", HttpStatus.INTERNAL_SERVER_ERROR),
    DOCUMENT_TYPE_NOT_SUPPORT(3504, "不支持的文件类型", HttpStatus.BAD_REQUEST),
    DOCUMENT_SIZE_EXCEED(3505, "文件大小超过限制", HttpStatus.BAD_REQUEST),

    // API凭证相关 (36xx)
    API_CREDENTIAL_NOT_FOUND(3601, "API凭证不存在", HttpStatus.NOT_FOUND),
    API_CREDENTIAL_DISABLED(3602, "API凭证已禁用", HttpStatus.FORBIDDEN),
    API_CREDENTIAL_EXPIRED(3603, "API凭证已过期", HttpStatus.FORBIDDEN),
    API_KEY_INVALID(3604, "API Key无效", HttpStatus.UNAUTHORIZED),
    API_SECRET_INVALID(3605, "API Secret无效", HttpStatus.UNAUTHORIZED),

    // ==================== 参数校验错误 (4xxx) ====================
    PARAM_INVALID(4001, "参数无效", HttpStatus.BAD_REQUEST),
    PARAM_MISSING(4002, "缺少必要参数", HttpStatus.BAD_REQUEST),
    PARAM_FORMAT_ERROR(4003, "参数格式错误", HttpStatus.BAD_REQUEST),
    PARAM_TYPE_MISMATCH(4004, "参数类型不匹配", HttpStatus.BAD_REQUEST),
    PARAM_VALUE_INVALID(4005, "参数值不合法", HttpStatus.BAD_REQUEST),
    REQUEST_BODY_INVALID(4006, "请求体格式错误", HttpStatus.BAD_REQUEST),
    REQUEST_METHOD_NOT_ALLOWED(4007, "不支持的请求方法", HttpStatus.METHOD_NOT_ALLOWED),

    // ==================== 第三方服务错误 (5xxx) ====================
    AI_SERVICE_ERROR(5001, "AI服务调用失败", HttpStatus.BAD_GATEWAY),
    AI_SERVICE_TIMEOUT(5002, "AI服务响应超时", HttpStatus.GATEWAY_TIMEOUT),
    AI_SERVICE_QUOTA_EXCEED(5003, "AI服务配额已用尽", HttpStatus.TOO_MANY_REQUESTS),
    ES_SERVICE_ERROR(5004, "搜索引擎服务异常", HttpStatus.BAD_GATEWAY),
    ES_INDEX_NOT_FOUND(5005, "搜索索引不存在", HttpStatus.NOT_FOUND),
    MINIO_SERVICE_ERROR(5006, "文件存储服务异常", HttpStatus.BAD_GATEWAY),
    REDIS_SERVICE_ERROR(5007, "缓存服务异常", HttpStatus.BAD_GATEWAY),

    // ==================== 业务操作错误 (9xxx) ====================
    OPERATION_FAILED(9001, "操作失败", HttpStatus.INTERNAL_SERVER_ERROR),
    OPERATION_CONFLICT(9002, "操作冲突，请稍后重试", HttpStatus.CONFLICT),
    OPERATION_TIMEOUT(9003, "操作超时", HttpStatus.GATEWAY_TIMEOUT),
    RESOURCE_CONFLICT(9004, "资源冲突", HttpStatus.CONFLICT),
    RESOURCE_LOCKED(9005, "资源被锁定", HttpStatus.LOCKED),
    DATA_DUPLICATED(9006, "数据重复", HttpStatus.CONFLICT),
    DATA_INTEGRITY_ERROR(9007, "数据完整性错误", HttpStatus.BAD_REQUEST),
    FILE_UPLOAD_FAILED(9008, "文件上传失败", HttpStatus.INTERNAL_SERVER_ERROR),
    FILE_DOWNLOAD_FAILED(9009, "文件下载失败", HttpStatus.INTERNAL_SERVER_ERROR),
    ;

    /** 错误码 */
    private final Integer code;

    /** 错误消息 */
    private final String message;

    /** HTTP 状态码 */
    private final HttpStatus httpStatus;

    /**
     * 根据错误码获取枚举实例
     */
    public static ErrorCode getByCode(Integer code) {
        if (code == null) {
            return SYSTEM_ERROR;
        }
        for (ErrorCode errorCode : values()) {
            if (errorCode.code.equals(code)) {
                return errorCode;
            }
        }
        return SYSTEM_ERROR;
    }

    /**
     * 是否为成功状态
     */
    public boolean isSuccess() {
        return this == SUCCESS;
    }

    /**
     * 是否为客户端错误（4xx）
     */
    public boolean isClientError() {
        return httpStatus != null && httpStatus.is4xxClientError();
    }

    /**
     * 是否为服务器错误（5xx）
     */
    public boolean isServerError() {
        return httpStatus != null && httpStatus.is5xxServerError();
    }
}

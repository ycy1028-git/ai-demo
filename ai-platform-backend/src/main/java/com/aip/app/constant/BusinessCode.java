package com.aip.app.constant;

/**
 * 业务状态码常量
 */
public class BusinessCode {

    /** 成功 */
    public static final int SUCCESS = 200;

    /** 请求参数错误 */
    public static final int BAD_REQUEST = 400;

    /** 未授权 */
    public static final int UNAUTHORIZED = 401;

    /** 禁止访问 */
    public static final int FORBIDDEN = 403;

    /** 资源未找到 */
    public static final int NOT_FOUND = 404;

    /** 请求方法不支持 */
    public static final int METHOD_NOT_ALLOWED = 405;

    /** 请求过于频繁 */
    public static final int TOO_MANY_REQUESTS = 429;

    /** 服务器内部错误 */
    public static final int INTERNAL_ERROR = 500;

    /** 服务不可用 */
    public static final int SERVICE_UNAVAILABLE = 503;

    // ==================== 业务错误码 ====================

    /** 业务处理失败 */
    public static final int BUSINESS_ERROR = 1000;

    /** 数据不存在 */
    public static final int DATA_NOT_FOUND = 1001;

    /** 数据已存在 */
    public static final int DATA_ALREADY_EXISTS = 1002;

    /** 重复操作 */
    public static final int DUPLICATE_OPERATION = 1003;

    /** 操作被拒绝 */
    public static final int OPERATION_REJECTED = 1004;

    // ==================== AI服务错误码 ====================

    /** AI服务不可用 */
    public static final int AI_SERVICE_UNAVAILABLE = 2000;

    /** AI服务调用失败 */
    public static final int AI_SERVICE_ERROR = 2001;

    /** AI服务响应超时 */
    public static final int AI_SERVICE_TIMEOUT = 2002;

    /** 模型不支持 */
    public static final int MODEL_NOT_SUPPORTED = 2003;

    /** Token超出限制 */
    public static final int TOKEN_LIMIT_EXCEEDED = 2004;

    // ==================== 知识库错误码 ====================

    /** 知识库不存在 */
    public static final int KNOWLEDGE_BASE_NOT_FOUND = 3000;

    /** 知识库已存在 */
    public static final int KNOWLEDGE_BASE_EXISTS = 3001;

    /** 文档处理失败 */
    public static final int DOCUMENT_PROCESS_ERROR = 3002;

    /** 索引创建失败 */
    public static final int INDEX_CREATE_ERROR = 3003;

    /** 向量化失败 */
    public static final int EMBEDDING_ERROR = 3004;

    /** 检索失败 */
    public static final int SEARCH_ERROR = 3005;

    private BusinessCode() {
        // 私有构造函数，防止实例化
    }
}

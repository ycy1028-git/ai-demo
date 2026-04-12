package com.aip.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 业务异常
 *
 * <p>支持两种构造方式：
 * <ul>
 *   <li>使用 ErrorCode 枚举：{@code new BusinessException(ErrorCode.USER_NOT_FOUND)}</li>
 *   <li>使用自定义错误码：{@code new BusinessException(3001, "用户不存在")}</li>
 * </ul>
 */
@Getter
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 错误码 */
    private final Integer code;

    /** HTTP 状态码 */
    private final HttpStatus httpStatus;

    /** 错误码枚举（可选） */
    private final ErrorCode errorCode;

    /**
     * 默认构造器（系统错误）
     */
    public BusinessException() {
        super("系统异常");
        this.code = 500;
        this.httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        this.errorCode = null;
    }

    /**
     * 使用错误消息构造
     */
    public BusinessException(String message) {
        super(message);
        this.code = 500;
        this.httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        this.errorCode = null;
    }

    /**
     * 使用错误码和消息构造
     */
    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
        this.httpStatus = HttpStatus.BAD_REQUEST;
        this.errorCode = null;
    }

    /**
     * 使用 ErrorCode 枚举构造
     */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
        this.httpStatus = errorCode.getHttpStatus();
        this.errorCode = errorCode;
    }

    /**
     * 使用 ErrorCode 和自定义消息构造
     */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
        this.httpStatus = errorCode.getHttpStatus();
        this.errorCode = errorCode;
    }

    /**
     * 使用错误消息和异常原因构造
     */
    public BusinessException(String message, Throwable cause) {
        super(message, cause);
        this.code = 500;
        this.httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        this.errorCode = null;
    }

    /**
     * 使用错误码、消息和异常原因构造
     */
    public BusinessException(Integer code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.httpStatus = HttpStatus.BAD_REQUEST;
        this.errorCode = null;
    }

    /**
     * 使用 ErrorCode 和异常原因构造
     */
    public BusinessException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.code = errorCode.getCode();
        this.httpStatus = errorCode.getHttpStatus();
        this.errorCode = errorCode;
    }

    /**
     * 获取错误码字符串（用于前端）
     */
    public String getErrorCodeStr() {
        return code != null ? String.valueOf(code) : null;
    }
}

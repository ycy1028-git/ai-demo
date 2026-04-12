package com.aip.common.result;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 统一响应结果封装
 *
 * @param <T> 数据类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 状态码 */
    private Integer code;

    /** 消息 */
    private String message;

    /** 数据 */
    private T data;

    /** 时间戳 */
    private Long timestamp;

    /** 是否成功 */
    private Boolean success;

    /** 错误码（可选，用于前端快速判断） */
    private String errorCode;

    // ==================== 成功响应 ====================

    /**
     * 快速成功响应（无数据）
     */
    public static <T> Result<T> ok() {
        return ok(null);
    }

    /**
     * 成功响应（带数据）
     */
    public static <T> Result<T> ok(T data) {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMessage("success");
        result.setSuccess(true);
        result.setData(data);
        result.setTimestamp(System.currentTimeMillis());
        return result;
    }

    /**
     * 成功响应（自定义消息）
     */
    public static <T> Result<T> ok(String message, T data) {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMessage(message);
        result.setSuccess(true);
        result.setData(data);
        result.setTimestamp(System.currentTimeMillis());
        return result;
    }

    /**
     * 成功响应（仅消息）
     */
    public static <T> Result<T> okMessage(String message) {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMessage(message);
        result.setSuccess(true);
        result.setTimestamp(System.currentTimeMillis());
        return result;
    }

    // ==================== 失败响应 ====================

    /**
     * 默认失败响应
     */
    public static <T> Result<T> fail() {
        Result<T> result = new Result<>();
        result.setCode(500);
        result.setMessage("操作失败");
        result.setSuccess(false);
        result.setTimestamp(System.currentTimeMillis());
        return result;
    }

    /**
     * 失败响应（自定义消息）
     */
    public static <T> Result<T> fail(String message) {
        Result<T> result = new Result<>();
        result.setCode(500);
        result.setMessage(message);
        result.setSuccess(false);
        result.setTimestamp(System.currentTimeMillis());
        return result;
    }

    /**
     * 失败响应（自定义状态码和消息）
     */
    public static <T> Result<T> fail(Integer code, String message) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMessage(message);
        result.setSuccess(false);
        result.setTimestamp(System.currentTimeMillis());
        return result;
    }

    /**
     * 失败响应（带错误码）
     */
    public static <T> Result<T> fail(Integer code, String message, String errorCode) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMessage(message);
        result.setSuccess(false);
        result.setErrorCode(errorCode);
        result.setTimestamp(System.currentTimeMillis());
        return result;
    }

    // ==================== 通用响应 ====================

    /**
     * 通用响应
     */
    public static <T> Result<T> of(Integer code, String message, T data) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMessage(message);
        result.setData(data);
        result.setSuccess(code != null && code < 400);
        result.setTimestamp(System.currentTimeMillis());
        return result;
    }

    // ==================== 实例方法 ====================

    /**
     * 链式调用：设置消息
     */
    public Result<T> message(String message) {
        this.message = message;
        return this;
    }

    /**
     * 链式调用：设置数据
     */
    public Result<T> data(T data) {
        this.data = data;
        return this;
    }

    /**
     * 链式调用：设置状态码
     */
    public Result<T> code(Integer code) {
        this.code = code;
        return this;
    }

    /**
     * 链式调用：设置成功状态
     */
    public Result<T> success(Boolean success) {
        this.success = success;
        return this;
    }

    /**
     * 链式调用：设置错误码
     */
    public Result<T> errorCode(String errorCode) {
        this.errorCode = errorCode;
        return this;
    }
}

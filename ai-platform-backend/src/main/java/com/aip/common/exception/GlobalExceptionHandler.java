package com.aip.common.exception;

import com.aip.common.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器
 *
 * <p>统一处理所有异常，返回标准化的 JSON 响应
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    // ==================== 业务异常处理 ====================

    /**
     * 业务异常处理
     */
    @ExceptionHandler(BusinessException.class)
    public Result<?> handleBusinessException(BusinessException e, HttpServletRequest request) {
        log.error("业务异常 - URI: {}, Code: {}, Message: {}", request.getRequestURI(), e.getCode(), e.getMessage());

        if (e.getErrorCode() != null) {
            ErrorCode errorCode = e.getErrorCode();
            Result<Void> result = new Result<>();
            result.setCode(errorCode.getHttpStatus().value());
            result.setSuccess(false);
            result.setErrorCode(String.valueOf(errorCode.getCode()));
            result.setMessage(e.getMessage());
            return result;
        }

        return Result.fail(e.getCode(), e.getMessage());
    }

    // ==================== 参数校验异常处理 ====================

    /**
     * 参数校验异常（@Valid 校验失败）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleValidationException(MethodArgumentNotValidException e, HttpServletRequest request) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));

        log.warn("参数校验失败 - URI: {}, Errors: {}", request.getRequestURI(), message);

        Result<Void> result = new Result<>();
        result.setCode(HttpStatus.BAD_REQUEST.value());
        result.setSuccess(false);
        result.setErrorCode(String.valueOf(ErrorCode.PARAM_INVALID.getCode()));
        result.setMessage(message);
        return result;
    }

    /**
     * 绑定异常（表单参数绑定失败）
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleBindException(BindException e, HttpServletRequest request) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));

        log.warn("参数绑定失败 - URI: {}, Errors: {}", request.getRequestURI(), message);

        Result<Void> result = new Result<>();
        result.setCode(HttpStatus.BAD_REQUEST.value());
        result.setSuccess(false);
        result.setErrorCode(String.valueOf(ErrorCode.PARAM_INVALID.getCode()));
        result.setMessage(message);
        return result;
    }

    /**
     * 缺少请求参数
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleMissingServletRequestParameterException(
            MissingServletRequestParameterException e, HttpServletRequest request) {
        String message = String.format("缺少必填参数: %s", e.getParameterName());
        log.warn("缺少请求参数 - URI: {}, Parameter: {}", request.getRequestURI(), e.getParameterName());

        Result<Void> result = new Result<>();
        result.setCode(HttpStatus.BAD_REQUEST.value());
        result.setSuccess(false);
        result.setErrorCode(String.valueOf(ErrorCode.PARAM_MISSING.getCode()));
        result.setMessage(message);
        return result;
    }

    /**
     * 参数类型不匹配
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        String message = String.format("参数 %s 类型错误，期望类型: %s",
                e.getName(), e.getRequiredType() != null ? e.getRequiredType().getSimpleName() : "未知");
        log.warn("参数类型不匹配 - URI: {}, Parameter: {}", request.getRequestURI(), e.getName());

        Result<Void> result = new Result<>();
        result.setCode(HttpStatus.BAD_REQUEST.value());
        result.setSuccess(false);
        result.setErrorCode(String.valueOf(ErrorCode.PARAM_TYPE_MISMATCH.getCode()));
        result.setMessage(message);
        return result;
    }

    /**
     * 非法参数异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleIllegalArgumentException(IllegalArgumentException e, HttpServletRequest request) {
        log.warn("非法参数 - URI: {}, Message: {}", request.getRequestURI(), e.getMessage());

        Result<Void> result = new Result<>();
        result.setCode(HttpStatus.BAD_REQUEST.value());
        result.setSuccess(false);
        result.setErrorCode(String.valueOf(ErrorCode.PARAM_INVALID.getCode()));
        result.setMessage(e.getMessage());
        return result;
    }

    // ==================== HTTP 请求异常处理 ====================

    /**
     * 不支持的媒体类型
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    @ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    public Result<?> handleHttpMediaTypeNotSupportedException(
            HttpMediaTypeNotSupportedException e, HttpServletRequest request) {
        String message = String.format("不支持的媒体类型: %s", e.getContentType());
        log.warn("不支持的媒体类型 - URI: {}, ContentType: {}", request.getRequestURI(), e.getContentType());

        Result<Void> result = new Result<>();
        result.setCode(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value());
        result.setSuccess(false);
        result.setErrorCode(String.valueOf(ErrorCode.REQUEST_BODY_INVALID.getCode()));
        result.setMessage(message);
        return result;
    }

    /**
     * 不支持的请求方法
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public Result<?> handleHttpRequestMethodNotSupportedException(
            HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        String message = String.format("不支持的请求方法: %s", e.getMethod());
        log.warn("不支持的请求方法 - URI: {}, Method: {}", request.getRequestURI(), e.getMethod());

        Result<Void> result = new Result<>();
        result.setCode(HttpStatus.METHOD_NOT_ALLOWED.value());
        result.setSuccess(false);
        result.setErrorCode(String.valueOf(ErrorCode.REQUEST_METHOD_NOT_ALLOWED.getCode()));
        result.setMessage(message);
        return result;
    }

    /**
     * 文件上传大小超限
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public Result<?> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException e, HttpServletRequest request) {
        log.warn("文件大小超限 - URI: {}", request.getRequestURI());

        Result<Void> result = new Result<>();
        result.setCode(HttpStatus.PAYLOAD_TOO_LARGE.value());
        result.setSuccess(false);
        result.setErrorCode(String.valueOf(ErrorCode.DOCUMENT_SIZE_EXCEED.getCode()));
        result.setMessage(ErrorCode.DOCUMENT_SIZE_EXCEED.getMessage());
        return result;
    }

    /**
     * 404 Not Found
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<?> handleNoHandlerFoundException(NoHandlerFoundException e, HttpServletRequest request) {
        String message = String.format("接口不存在: %s %s", e.getHttpMethod(), e.getRequestURL());
        log.warn("接口不存在 - URI: {}", request.getRequestURI());

        Result<Void> result = new Result<>();
        result.setCode(HttpStatus.NOT_FOUND.value());
        result.setSuccess(false);
        result.setMessage(message);
        return result;
    }

    // ==================== 系统异常处理 ====================

    /**
     * 空指针异常
     */
    @ExceptionHandler(NullPointerException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<?> handleNullPointerException(NullPointerException e, HttpServletRequest request) {
        log.error("空指针异常 - URI: {}", request.getRequestURI(), e);

        Result<Void> result = new Result<>();
        result.setCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        result.setSuccess(false);
        result.setErrorCode(String.valueOf(ErrorCode.SYSTEM_ERROR.getCode()));
        result.setMessage("数据处理异常，请稍后重试");
        return result;
    }

    /**
     * 类型转换异常
     */
    @ExceptionHandler(ClassCastException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<?> handleClassCastException(ClassCastException e, HttpServletRequest request) {
        log.error("类型转换异常 - URI: {}", request.getRequestURI(), e);

        Result<Void> result = new Result<>();
        result.setCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        result.setSuccess(false);
        result.setErrorCode(String.valueOf(ErrorCode.DATA_PROCESSING_ERROR.getCode()));
        result.setMessage("数据处理异常");
        return result;
    }

    /**
     * 算术异常
     */
    @ExceptionHandler(ArithmeticException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<?> handleArithmeticException(ArithmeticException e, HttpServletRequest request) {
        log.error("算术异常 - URI: {}", request.getRequestURI(), e);

        Result<Void> result = new Result<>();
        result.setCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        result.setSuccess(false);
        result.setErrorCode(String.valueOf(ErrorCode.DATA_PROCESSING_ERROR.getCode()));
        result.setMessage("计算异常");
        return result;
    }

    /**
     * 数组越界异常
     */
    @ExceptionHandler(ArrayIndexOutOfBoundsException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<?> handleArrayIndexOutOfBoundsException(
            ArrayIndexOutOfBoundsException e, HttpServletRequest request) {
        log.error("数组越界异常 - URI: {}", request.getRequestURI(), e);

        Result<Void> result = new Result<>();
        result.setCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        result.setSuccess(false);
        result.setErrorCode(String.valueOf(ErrorCode.DATA_PROCESSING_ERROR.getCode()));
        result.setMessage("数据处理异常");
        return result;
    }

    // ==================== 通用异常处理 ====================

    /**
     * 其他异常（兜底）
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<?> handleException(Exception e, HttpServletRequest request) {
        log.error("系统异常 - URI: {}, Type: {}, Message: {}",
                request.getRequestURI(), e.getClass().getSimpleName(), e.getMessage(), e);

        Result<Void> result = new Result<>();
        result.setCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        result.setSuccess(false);
        result.setErrorCode(String.valueOf(ErrorCode.SYSTEM_ERROR.getCode()));
        result.setMessage(ErrorCode.SYSTEM_ERROR.getMessage());
        return result;
    }
}

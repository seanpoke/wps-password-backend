package com.docauth.exception;

import com.docauth.dto.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.server.ResponseStatusException;

/**
 * 全局异常处理器：将业务异常转换为统一的 ApiResponse，并设置正确的 HTTP 状态码。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ApiResponse<?> handleApiException(ApiException ex, HttpServletResponse response) {
        response.setStatus(ex.getHttpStatus());
        return ApiResponse.error(ex.getHttpStatus(), ex.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public ApiResponse<?> handleRuntimeException(RuntimeException ex, HttpServletResponse response) {
        // 服务层用 RuntimeException 表达业务校验失败（如“部门不存在”），映射为 400
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        return ApiResponse.error(HttpStatus.BAD_REQUEST.value(), ex.getMessage());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ApiResponse<?> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, HttpServletResponse response) {
        // 错误的 HTTP 方法（如用 POST 调 GET 接口）应保持 405，不掩盖为 500
        response.setStatus(HttpStatus.METHOD_NOT_ALLOWED.value());
        return ApiResponse.error(HttpStatus.METHOD_NOT_ALLOWED.value(), ex.getMessage());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ApiResponse<?> handleResponseStatus(ResponseStatusException ex, HttpServletResponse response) {
        response.setStatus(ex.getStatusCode().value());
        return ApiResponse.error(ex.getStatusCode().value(), ex.getReason());
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<?> handleUnexpected(Exception ex, HttpServletResponse response) {
        log.error("未预期异常", ex);
        response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        return ApiResponse.error(HttpStatus.INTERNAL_SERVER_ERROR.value(), "服务器内部错误：" + ex.getMessage());
    }
}

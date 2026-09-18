package com.docauth.exception;

/**
 * 业务异常，携带 HTTP 状态码。由 GlobalExceptionHandler 统一转换为 ApiResponse。
 */
public class ApiException extends RuntimeException {

    private final int httpStatus;

    public ApiException(int httpStatus, String message) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public ApiException(int httpStatus, String message, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}

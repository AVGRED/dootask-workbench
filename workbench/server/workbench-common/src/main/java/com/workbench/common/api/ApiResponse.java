package com.workbench.common.api;

/**
 * 统一返回体：{@code {code, message, data, traceId}}。
 *
 * <p>与旧系统 {@code Base::retSuccess/retError} 的语义保持一致：{@code code == 0}
 * 表示成功，非 0 为业务错误码；{@code traceId} 用于串联日志。</p>
 */
public record ApiResponse<T>(int code, String message, T data, String traceId) {

    public static final int OK = 0;

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(OK, "ok", data, null);
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(OK, message, data, null);
    }

    public static <T> ApiResponse<T> fail(int code, String message) {
        return new ApiResponse<>(code, message, null, null);
    }

    public ApiResponse<T> withTraceId(String traceId) {
        return new ApiResponse<>(code, message, data, traceId);
    }

    public boolean isOk() {
        return code == OK;
    }
}

package com.workbench.common.api;

/**
 * 业务异常：携带错误码与用户可见文案，由全局异常处理器统一转成 {@link ApiResponse}。
 *
 * <p>对应旧系统的 {@code ApiException}：业务层直接抛，不要返回 null 或拼错误字符串。</p>
 */
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(String message) {
        this(400, message);
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}

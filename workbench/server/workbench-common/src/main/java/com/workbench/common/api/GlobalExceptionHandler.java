package com.workbench.common.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理：把业务异常、参数校验失败与未捕获异常统一转成 {@link ApiResponse}。
 *
 * <p>HTTP 状态码保持 200，业务结果由 {@code code} 表达——与旧系统一致，前端只需判断
 * {@code code == 0}。未捕获异常返回 500 并记录完整堆栈，便于排查。</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException exception) {
        return ResponseEntity.ok(withTrace(ApiResponse.fail(exception.getCode(), exception.getMessage())));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException exception) {
        FieldError fieldError = exception.getBindingResult().getFieldError();
        String message = fieldError == null
                ? "参数校验失败"
                : fieldError.getField() + ": " + fieldError.getDefaultMessage();
        return ResponseEntity.ok(withTrace(ApiResponse.fail(400, message)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception exception) {
        log.error("unhandled exception", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(withTrace(ApiResponse.fail(500, "服务器内部错误")));
    }

    private ApiResponse<Void> withTrace(ApiResponse<Void> response) {
        String traceId = MDC.get("traceId");
        return traceId == null ? response : response.withTraceId(traceId);
    }
}

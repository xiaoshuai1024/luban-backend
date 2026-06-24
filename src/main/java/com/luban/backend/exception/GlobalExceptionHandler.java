package com.luban.backend.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 敏感关键词：异常消息含这些词时脱敏（T-be-8，防泄漏 contact/密钥明文）。 */
    private static final String[] SENSITIVE_KEYWORDS = {
            "contact", "phone", "email", "decrypt", "encrypt", "key", "密码", "手机", "邮箱"
    };

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<APIError> handleBusiness(BusinessException ex) {
        return ResponseEntity
                .status(ex.getHttpStatus())
                .body(new APIError(ex.getCode(), ex.getMessage(), ex.getDetails()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<APIError> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .orElse("请求参数非法");
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new APIError("INVALID_ARGUMENT", msg));
    }

    /**
    /**
     * Malformed / unreadable JSON body (e.g. truncated payload, invalid JSON syntax).
     * Aligned with the Go backend's {@code ShouldBindJSON} failure path → 400
     * INVALID_ARGUMENT (plan §9.2). Without this handler Spring would fall through to
     * {@link #handleOther} and return 500 INTERNAL, breaking dual-backend parity.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<APIError> handleUnreadable(HttpMessageNotReadableException ex) {
        String msg = ex.getMessage() != null && ex.getMessage().contains("required")
                ? "request body is required"
                : "malformed JSON body";
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new APIError("INVALID_ARGUMENT", msg));
    }

    /**
     * T-be-8 安全加固：500 异常消息脱敏。
     * 完整异常栈记日志（便于排查），但返回给客户端的消息移除敏感关键词。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<APIError> handleOther(Exception ex) {
        log.error("未处理异常（脱敏后返回客户端）", ex);
        String rawMsg = ex.getMessage() != null ? ex.getMessage() : "internal error";
        String safeMsg = maskSensitive(rawMsg);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new APIError("INTERNAL", safeMsg));
    }

    /** 消息含敏感关键词时替换为通用提示。 */
    private static String maskSensitive(String msg) {
        if (msg == null) return "internal error";
        String lower = msg.toLowerCase();
        for (String kw : SENSITIVE_KEYWORDS) {
            if (lower.contains(kw.toLowerCase())) {
                return "服务器内部错误（详细信息已脱敏）";
            }
        }
        return msg;
    }
}

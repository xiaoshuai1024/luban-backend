package com.luban.backend.exception;

import org.springframework.http.HttpStatus;

/**
 * Business error with HTTP status and API error code for global handler.
 */
public class BusinessException extends RuntimeException {

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
    private final Object details;

    public HttpStatus getHttpStatus() { return httpStatus; }
    public String getCode() { return code; }
    @Override
    public String getMessage() { return message; }
    public Object getDetails() { return details; }

    public BusinessException(HttpStatus httpStatus, String code, String message) {
        this(httpStatus, code, message, null);
    }

    public BusinessException(HttpStatus httpStatus, String code, String message, Object details) {
        super(message);
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
        this.details = details;
    }

    public static BusinessException siteNotFound() {
        return new BusinessException(HttpStatus.NOT_FOUND, "SITE_NOT_FOUND", "站点不存在");
    }

    public static BusinessException pageNotFound() {
        return new BusinessException(HttpStatus.NOT_FOUND, "PAGE_NOT_FOUND", "页面不存在");
    }

    public static BusinessException userNotFound() {
        return new BusinessException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在");
    }

    public static BusinessException pagePathConflict() {
        return new BusinessException(HttpStatus.CONFLICT, "PAGE_PATH_CONFLICT", "页面 path 已存在");
    }

    public static BusinessException usernameConflict() {
        return new BusinessException(HttpStatus.CONFLICT, "USERNAME_CONFLICT", "用户名已存在");
    }

    public static BusinessException slugConflict() {
        return new BusinessException(HttpStatus.CONFLICT, "SLUG_CONFLICT", "slug 已存在");
    }

    public static BusinessException invalidCredentials() {
        return new BusinessException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "账号或密码错误");
    }

    public static BusinessException userDisabled() {
        return new BusinessException(HttpStatus.FORBIDDEN, "USER_DISABLED", "用户已被禁用");
    }

    public static BusinessException unauthenticated() {
        return new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "missing user");
    }

    public static BusinessException permissionDenied() {
        return new BusinessException(HttpStatus.FORBIDDEN, "PERMISSION_DENIED", "admin only");
    }

    public static BusinessException invalidArgument(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", message != null ? message : "请求参数非法");
    }

    // ---- Lead / Form 留资相关（P0）----

    public static BusinessException leadNotFound() {
        return new BusinessException(HttpStatus.NOT_FOUND, "LEAD_NOT_FOUND", "线索不存在");
    }

    public static BusinessException formNotFound() {
        return new BusinessException(HttpStatus.NOT_FOUND, "FORM_NOT_FOUND", "表单不存在");
    }

    public static BusinessException leadDuplicate() {
        return new BusinessException(HttpStatus.CONFLICT, "LEAD_DUPLICATE", "重复留资");
    }

    public static BusinessException leadSpamBlocked() {
        return new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "LEAD_SPAM_BLOCKED", "操作过于频繁，请稍后再试");
    }

    public static BusinessException leadDisabled() {
        return new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "LEAD_DISABLED", "留资功能暂未开放");
    }

    public static BusinessException leadForbidden() {
        return new BusinessException(HttpStatus.FORBIDDEN, "LEAD_FORBIDDEN", "无权操作此线索");
    }

    public static BusinessException captchaInvalid() {
        return new BusinessException(HttpStatus.BAD_REQUEST, "LEAD_CAPTCHA_INVALID", "验证码错误");
    }

    public static BusinessException leadValidationFailed(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, "LEAD_VALIDATION_FAILED", message != null ? message : "留资信息校验失败");
    }
}

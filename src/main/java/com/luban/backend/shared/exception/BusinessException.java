package com.luban.backend.shared.exception;

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

    public static BusinessException formHasLeads() {
        return new BusinessException(HttpStatus.CONFLICT, "FORM_HAS_LEADS", "表单下存在线索，无法删除");
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

    // ---- 页面版本（plan §3.4）----

    public static BusinessException pageVersionNotFound() {
        return new BusinessException(HttpStatus.NOT_FOUND, "PAGE_VERSION_NOT_FOUND", "页面版本不存在");
    }

    // ---- FeatureGate（plan §3.5）----

    public static BusinessException featureGateNotFound() {
        return new BusinessException(HttpStatus.NOT_FOUND, "FEATURE_GATE_NOT_FOUND", "特性开关不存在");
    }

    // ---- Datasource 数据源相关（W1-T2）----

    public static BusinessException datasourceNotFound() {
        return new BusinessException(HttpStatus.NOT_FOUND, "DATASOURCE_NOT_FOUND", "数据源不存在");
    }

    public static BusinessException datasourceNameConflict() {
        return new BusinessException(HttpStatus.CONFLICT, "DATASOURCE_NAME_CONFLICT", "数据源名称已存在");
    }

    public static BusinessException datasourceConnectionFailed(String message) {
        return new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "DATASOURCE_CONNECTION_FAILED", message != null ? message : "数据源连接失败");
    }

    // ---- V2-T7 Collection CMS 内容集合相关 ----

    public static BusinessException collectionNotFound() {
        return new BusinessException(HttpStatus.NOT_FOUND, "COLLECTION_NOT_FOUND", "内容集合不存在");
    }

    public static BusinessException collectionNameConflict() {
        return new BusinessException(HttpStatus.CONFLICT, "COLLECTION_NAME_CONFLICT", "内容集合名称已存在");
    }

    public static BusinessException collectionItemNotFound() {
        return new BusinessException(HttpStatus.NOT_FOUND, "COLLECTION_ITEM_NOT_FOUND", "内容项不存在");
    }

    // ---- Campaign / Channel 渠道短链相关（app-deeplink-backend-arch plan T7）----

    public static BusinessException campaignNotFound() {
        return new BusinessException(HttpStatus.NOT_FOUND, "CAMPAIGN_NOT_FOUND", "活动不存在");
    }

    public static BusinessException channelNotFound() {
        return new BusinessException(HttpStatus.NOT_FOUND, "CHANNEL_NOT_FOUND", "渠道不存在");
    }

    /** shortCode 不存在（404，区分"不存在"与"已停用"） */
    public static BusinessException shortLinkNotFound() {
        return new BusinessException(HttpStatus.NOT_FOUND, "SHORT_LINK_NOT_FOUND", "短链不存在");
    }

    /** channel.status=inactive（410 Gone，区分"不存在"与"已停用"） */
    public static BusinessException shortLinkInactive() {
        return new BusinessException(HttpStatus.valueOf(410), "SHORT_LINK_INACTIVE", "短链已停用");
    }

    /** 同站短码重复（409，违反 uk_site_code） */
    public static BusinessException channelCodeDuplicate() {
        return new BusinessException(HttpStatus.CONFLICT, "CHANNEL_CODE_DUPLICATE", "短码已存在");
    }

    /** 短码生成碰撞重试耗尽（503） */
    public static BusinessException codeGenFailed() {
        return new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "CODE_GEN_FAILED", "短码生成失败，请重试");
    }

    /** channel.target_page_id 的 page.site_id ≠ channel.site_id（400，防开放重定向） */
    public static BusinessException pageNotBelongToSite() {
        return new BusinessException(HttpStatus.BAD_REQUEST, "PAGE_NOT_BELONG_TO_SITE", "目标页面不属于该站点");
    }

    /** 状态机非法转换（400） */
    public static BusinessException invalidStateTransition(String from, String to) {
        return new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_STATE_TRANSITION",
                "非法状态转换: " + from + " → " + to);
    }

    /** 活动时间窗非法 endAt < startAt（400） */
    public static BusinessException invalidTimeWindow() {
        return new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_TIME_WINDOW", "结束时间不能早于开始时间");
    }

    /** 短码格式不符 [a-zA-Z0-9_-]{1,32}（400） */
    public static BusinessException invalidCodeFormat() {
        return new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_CODE_FORMAT", "短码格式非法（仅允许字母数字下划线连字符，1-32 位）");
    }

    /** 必填字段缺失（400） */
    public static BusinessException missingField(String field) {
        return new BusinessException(HttpStatus.BAD_REQUEST, "MISSING_FIELD", "缺少必填字段: " + field);
    }
}

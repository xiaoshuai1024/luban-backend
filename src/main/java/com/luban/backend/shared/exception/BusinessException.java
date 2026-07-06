package com.luban.backend.shared.exception;

/**
 * 业务异常：携带原始 HTTP 状态码（int）+ API 错误码 + 消息。
 *
 * <p><b>domain-friendly</b>：本类不依赖任何框架（无 Spring/Jakarta），可被领域层（聚合根/值对象）
 * 安全引用。原始 {@code int statusCode} 在 {@link GlobalExceptionHandler}（基础设施边界）中
 * 映射为 {@code ResponseEntity.status(int)}。
 *
 * <p>所有领域层抛出的业务错误<b>必须</b>走本类的工厂方法（{@code BusinessException.xxx()}），
 * 禁止在 {@code shared/domain/**} 直接 {@code new BusinessException(int, ...)}。
 */
public class BusinessException extends RuntimeException {

    private final int statusCode;
    private final String code;
    private final String message;
    private final Object details;

    public int getStatusCode() { return statusCode; }
    public String getCode() { return code; }
    @Override
    public String getMessage() { return message; }
    public Object getDetails() { return details; }

    public BusinessException(int statusCode, String code, String message) {
        this(statusCode, code, message, null);
    }

    public BusinessException(int statusCode, String code, String message, Object details) {
        super(message);
        this.statusCode = statusCode;
        this.code = code;
        this.message = message;
        this.details = details;
    }

    public static BusinessException siteNotFound() {
        return new BusinessException(404, "SITE_NOT_FOUND", "站点不存在");
    }

    public static BusinessException pageNotFound() {
        return new BusinessException(404, "PAGE_NOT_FOUND", "页面不存在");
    }

    public static BusinessException userNotFound() {
        return new BusinessException(404, "USER_NOT_FOUND", "用户不存在");
    }

    public static BusinessException pagePathConflict() {
        return new BusinessException(409, "PAGE_PATH_CONFLICT", "页面 path 已存在");
    }

    public static BusinessException usernameConflict() {
        return new BusinessException(409, "USERNAME_CONFLICT", "用户名已存在");
    }

    public static BusinessException slugConflict() {
        return new BusinessException(409, "SLUG_CONFLICT", "slug 已存在");
    }

    public static BusinessException invalidCredentials() {
        return new BusinessException(401, "INVALID_CREDENTIALS", "账号或密码错误");
    }

    public static BusinessException userDisabled() {
        return new BusinessException(403, "USER_DISABLED", "用户已被禁用");
    }

    public static BusinessException unauthenticated() {
        return new BusinessException(401, "UNAUTHENTICATED", "missing user");
    }

    public static BusinessException permissionDenied() {
        return new BusinessException(403, "PERMISSION_DENIED", "admin only");
    }

    public static BusinessException invalidArgument(String message) {
        return new BusinessException(400, "INVALID_ARGUMENT", message != null ? message : "请求参数非法");
    }

    // ---- Lead / Form 留资相关（P0）----

    public static BusinessException leadNotFound() {
        return new BusinessException(404, "LEAD_NOT_FOUND", "线索不存在");
    }

    public static BusinessException formNotFound() {
        return new BusinessException(404, "FORM_NOT_FOUND", "表单不存在");
    }

    public static BusinessException leadDuplicate() {
        return new BusinessException(409, "LEAD_DUPLICATE", "重复留资");
    }

    public static BusinessException formHasLeads() {
        return new BusinessException(409, "FORM_HAS_LEADS", "表单下存在线索，无法删除");
    }

    public static BusinessException leadSpamBlocked() {
        return new BusinessException(429, "LEAD_SPAM_BLOCKED", "操作过于频繁，请稍后再试");
    }

    public static BusinessException leadDisabled() {
        return new BusinessException(503, "LEAD_DISABLED", "留资功能暂未开放");
    }

    public static BusinessException leadForbidden() {
        return new BusinessException(403, "LEAD_FORBIDDEN", "无权操作此线索");
    }

    public static BusinessException captchaInvalid() {
        return new BusinessException(400, "LEAD_CAPTCHA_INVALID", "验证码错误");
    }

    public static BusinessException leadValidationFailed(String message) {
        return new BusinessException(400, "LEAD_VALIDATION_FAILED", message != null ? message : "留资信息校验失败");
    }

    /** Lead 状态机非法转换（409，对齐 API §3.10）。 */
    public static BusinessException leadInvalidTransition(String from, String to) {
        return new BusinessException(409, "LEAD_INVALID_TRANSITION", "非法状态转移: " + from + " → " + to);
    }

    /** Lead 未知状态值（400）。 */
    public static BusinessException leadInvalidStatus(String raw) {
        return new BusinessException(400, "LEAD_INVALID_STATUS", "未知线索状态: " + raw);
    }

    // ---- 页面版本（plan §3.4）----

    public static BusinessException pageVersionNotFound() {
        return new BusinessException(404, "PAGE_VERSION_NOT_FOUND", "页面版本不存在");
    }

    // ---- FeatureGate（plan §3.5）----

    public static BusinessException featureGateNotFound() {
        return new BusinessException(404, "FEATURE_GATE_NOT_FOUND", "特性开关不存在");
    }

    // ---- Datasource 数据源相关（W1-T2）----

    public static BusinessException datasourceNotFound() {
        return new BusinessException(404, "DATASOURCE_NOT_FOUND", "数据源不存在");
    }

    public static BusinessException datasourceNameConflict() {
        return new BusinessException(409, "DATASOURCE_NAME_CONFLICT", "数据源名称已存在");
    }

    public static BusinessException datasourceConnectionFailed(String message) {
        return new BusinessException(503, "DATASOURCE_CONNECTION_FAILED", message != null ? message : "数据源连接失败");
    }

    // ---- V2-T7 Collection CMS 内容集合相关 ----

    public static BusinessException collectionNotFound() {
        return new BusinessException(404, "COLLECTION_NOT_FOUND", "内容集合不存在");
    }

    public static BusinessException collectionNameConflict() {
        return new BusinessException(409, "COLLECTION_NAME_CONFLICT", "内容集合名称已存在");
    }

    public static BusinessException collectionItemNotFound() {
        return new BusinessException(404, "COLLECTION_ITEM_NOT_FOUND", "内容项不存在");
    }

    // ---- Campaign / Channel 渠道短链相关（app-deeplink-backend-arch plan T7）----

    public static BusinessException campaignNotFound() {
        return new BusinessException(404, "CAMPAIGN_NOT_FOUND", "活动不存在");
    }

    public static BusinessException channelNotFound() {
        return new BusinessException(404, "CHANNEL_NOT_FOUND", "渠道不存在");
    }

    /** campaign 下有 channel 时拒绝删除（409，防 FK RESTRICT 500） */
    public static BusinessException campaignHasChannels() {
        return new BusinessException(409, "CAMPAIGN_HAS_CHANNELS", "活动下仍有渠道，无法删除");
    }

    /** shortCode 不存在（404，区分"不存在"与"已停用"） */
    public static BusinessException shortLinkNotFound() {
        return new BusinessException(404, "SHORT_LINK_NOT_FOUND", "短链不存在");
    }

    /** channel.status=inactive（410 Gone，区分"不存在"与"已停用"） */
    public static BusinessException shortLinkInactive() {
        return new BusinessException(410, "SHORT_LINK_INACTIVE", "短链已停用");
    }

    /** 同站短码重复（409，违反 uk_site_code） */
    public static BusinessException channelCodeDuplicate() {
        return new BusinessException(409, "CHANNEL_CODE_DUPLICATE", "短码已存在");
    }

    /** 短码生成碰撞重试耗尽（503） */
    public static BusinessException codeGenFailed() {
        return new BusinessException(503, "CODE_GEN_FAILED", "短码生成失败，请重试");
    }

    /** channel.target_page_id 的 page.site_id ≠ channel.site_id（400，防开放重定向） */
    public static BusinessException pageNotBelongToSite() {
        return new BusinessException(400, "PAGE_NOT_BELONG_TO_SITE", "目标页面不属于该站点");
    }

    /** 状态机非法转换（400） */
    public static BusinessException invalidStateTransition(String from, String to) {
        return new BusinessException(400, "INVALID_STATE_TRANSITION",
                "非法状态转换: " + from + " → " + to);
    }

    /** 活动时间窗非法 endAt < startAt（400） */
    public static BusinessException invalidTimeWindow() {
        return new BusinessException(400, "INVALID_TIME_WINDOW", "结束时间不能早于开始时间");
    }

    /** 短码格式不符 [a-zA-Z0-9_-]{1,32}（400） */
    public static BusinessException invalidCodeFormat() {
        return new BusinessException(400, "INVALID_CODE_FORMAT", "短码格式非法（仅允许字母数字下划线连字符，1-32 位）");
    }

    /** 必填字段缺失（400） */
    public static BusinessException missingField(String field) {
        return new BusinessException(400, "MISSING_FIELD", "缺少必填字段: " + field);
    }

    // ---- AB 实验 ----

    /** 实验不存在（404）。 */
    public static BusinessException experimentNotFound() {
        return new BusinessException(404, "EXPERIMENT_NOT_FOUND", "实验不存在");
    }

    /** 单页单 running 冲突（409）。 */
    public static BusinessException experimentConflict(String detail) {
        return new BusinessException(409, "EXPERIMENT_CONFLICT",
                detail != null ? detail : "该页面已有运行中的实验");
    }

    /** 变体数量不足（400，computeSignificance 至少 2 个变体）。 */
    public static BusinessException insufficientVariants() {
        return new BusinessException(400, "INSUFFICIENT_VARIANTS", "至少需要 2 个变体");
    }

    // ---- Subscription / 配额 ----

    /** 配额超限（429）。 */
    public static BusinessException quotaExceeded(String message) {
        return new BusinessException(429, "QUOTA_EXCEEDED",
                message != null ? message : "配额超限");
    }

    // === 模板市场（template-marketplace）===

    /** 模板不存在（404） */
    public static BusinessException templateNotFound() {
        return new BusinessException(404, "TEMPLATE_NOT_FOUND", "模板不存在");
    }

    /** 模板未发布（不可安装）（409） */
    public static BusinessException templateNotPublished() {
        return new BusinessException(409, "TEMPLATE_NOT_PUBLISHED", "模板未发布，不可安装");
    }

    /** 模板 slug 冲突（409） */
    public static BusinessException templateSlugConflict() {
        return new BusinessException(409, "TEMPLATE_SLUG_CONFLICT", "模板 slug 已存在");
    }

    /** 模板类目非法（400） */
    public static BusinessException templateInvalidCategory() {
        return new BusinessException(400, "TEMPLATE_INVALID_CATEGORY", "模板类目非法");
    }

    /** 模板 slug 格式非法（400） */
    public static BusinessException templateInvalidSlug() {
        return new BusinessException(400, "TEMPLATE_INVALID_SLUG", "模板 slug 格式非法（仅允许字母数字下划线短横线，1-128 位）");
    }

    /** 模板状态机非法转移（409） */
    public static BusinessException templateInvalidStatusTransition(String from, String to) {
        return new BusinessException(409, "TEMPLATE_INVALID_TRANSITION", "模板状态非法转移: " + from + " → " + to);
    }

    /** 模板 schema 为空（不可发布）（400） */
    public static BusinessException templateSchemaEmpty() {
        return new BusinessException(400, "TEMPLATE_SCHEMA_EMPTY", "模板 schema 不能为空");
    }
}

package com.luban.backend.shared.domain;

import com.luban.backend.shared.exception.BusinessException;

import java.util.Set;

/**
 * TemplateAggregate — 模板市场聚合根（DDD）。
 *
 * <p>封装模板的不变量与状态机，纯领域类（无 Spring 依赖）。
 * 由 {@code TemplateService} 调用，对齐 {@code CampaignAggregate} 范式。
 *
 * <h3>状态机</h3>
 * <pre>
 *   draft ──publish──▶ published ──archive──▶ archived
 *                       │
 *                       └──feature──▶ featured ──unfeature──▶ published
 * </pre>
 *
 * <h3>不变量</h3>
 * <ul>
 *   <li>category 必须在白名单内</li>
 *   <li>slug 非空且符合 url-safe 格式</li>
 *   <li>schema_json 非空（发布时校验）</li>
 * </ul>
 *
 * <p>归属：template-marketplace plan。
 */
public final class TemplateAggregate {

    /** 允许的类目白名单（与前端 templates.ts 的 category 联合类型对齐）。 */
    public static final Set<String> ALLOWED_CATEGORIES = Set.of(
            "blank", "saas", "ecommerce", "education", "blog", "landing", "portfolio"
    );

    /** slug 格式：1-128 位 url-safe 字符。 */
    public static final String SLUG_PATTERN = "^[a-zA-Z0-9_-]{1,128}$";

    public static final String STATUS_DRAFT = "draft";
    public static final String STATUS_PUBLISHED = "published";
    public static final String STATUS_ARCHIVED = "archived";
    public static final String STATUS_FEATURED = "featured";

    private TemplateAggregate() {
        // 工具类，不实例化
    }

    // === 类目校验 ===

    public static void validateCategory(String category) {
        if (category == null || !ALLOWED_CATEGORIES.contains(category)) {
            throw BusinessException.templateInvalidCategory();
        }
    }

    public static void validateSlug(String slug) {
        if (slug == null || !slug.matches(SLUG_PATTERN)) {
            throw BusinessException.templateInvalidSlug();
        }
    }

    // === 状态机 ===

    /**
     * 校验状态转移合法性。
     *
     * <ul>
     *   <li>draft → published / archived</li>
     *   <li>published → archived / featured</li>
     *   <li>featured → published / archived</li>
     *   <li>archived → published（重新上架）</li>
     * </ul>
     */
    public static String transitionStatus(String current, String target) {
        if (!isValidTransition(current, target)) {
            throw BusinessException.templateInvalidStatusTransition(current, target);
        }
        return target;
    }

    private static boolean isValidTransition(String from, String to) {
        if (from == null || to == null) return false;
        return switch (from) {
            case STATUS_DRAFT -> Set.of(STATUS_PUBLISHED, STATUS_ARCHIVED).contains(to);
            case STATUS_PUBLISHED -> Set.of(STATUS_ARCHIVED, STATUS_FEATURED).contains(to);
            case STATUS_FEATURED -> Set.of(STATUS_PUBLISHED, STATUS_ARCHIVED).contains(to);
            case STATUS_ARCHIVED -> Set.of(STATUS_PUBLISHED).contains(to);
            default -> false;
        };
    }

    /** 模板是否对市场可见（published 或 featured）。 */
    public static boolean isMarketplaceVisible(String status) {
        return STATUS_PUBLISHED.equals(status) || STATUS_FEATURED.equals(status);
    }
}

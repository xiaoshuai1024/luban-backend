package com.luban.backend.shared.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.luban.backend.shared.domain.event.DomainEvent;
import com.luban.backend.shared.domain.event.TemplateInstalledEvent;
import com.luban.backend.shared.entity.Template;
import com.luban.backend.shared.entity.TemplateVersion;
import com.luban.backend.shared.exception.BusinessException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Template 聚合根（backend-ddd-refactor plan v2 T13，重写静态工具类为真聚合根）。
 *
 * <p>v2 纠正（对齐 {@code .agents/rules/luban-engineering-principles.md} §2 反模式）：
 * 旧版是全 static 工具类（private 构造、操作外部 status 字符串）——属"静态工具类伪装聚合根"反模式。
 * 重写为真聚合根：final + 持有 Template 实体引用 + 充血实例方法 + 工厂 + pullEvents。
 *
 * <p><b>实例方法封装的不变量</b>（真聚合根职责）：
 * <ul>
 *   <li>工厂 newTemplate：初始 status=draft + latestVersion=1 + 初始版本快照</li>
 *   <li>publish/archive/feature：状态机（draft→published→archived/featured），发布前须有 schema</li>
 *   <li>updateSchema：schema 变更产新版本（latestVersion+1）</li>
 *   <li>install：校验 marketplace 可见 + 产 TemplateInstalledEvent（跨聚合解耦，handler 创建 page）</li>
 * </ul>
 *
 * <p>保留为 static 的无状态校验（合法纯函数，非反模式）：validateCategory / validateSlug。
 */
public final class TemplateAggregate {

    public static final Set<String> ALLOWED_CATEGORIES = Set.of(
            "blank", "saas", "ecommerce", "education", "blog", "landing", "portfolio"
    );
    public static final String SLUG_PATTERN = "^[a-zA-Z0-9_-]{1,128}$";

    public static final String STATUS_DRAFT = "draft";
    public static final String STATUS_PUBLISHED = "published";
    public static final String STATUS_ARCHIVED = "archived";
    public static final String STATUS_FEATURED = "featured";

    private final Template root;
    private TemplateVersion pendingNewVersion;
    private final List<DomainEvent> events = new ArrayList<>();

    private TemplateAggregate(Template root) {
        this.root = root;
    }

    /** 工厂：创建新模板（初始 status=draft + latestVersion=1 + 初始版本快照）。 */
    public static TemplateAggregate newTemplate(String id, String slug, String name, String category,
                                                String description, String thumbnail, String authorId,
                                                String schemaJson, String changeNote) {
        validateSlug(slug);
        validateCategory(category);
        if (schemaJson == null || schemaJson.isBlank()) {
            throw BusinessException.templateSchemaEmpty();
        }
        Instant now = Instant.now();
        Template t = new Template();
        t.setId(id);
        t.setSlug(slug);
        t.setName(name);
        t.setCategory(category);
        t.setDescription(description);
        t.setThumbnail(thumbnail != null ? thumbnail : "📄");
        t.setAuthorId(authorId);
        t.setStatus(STATUS_DRAFT);
        t.setLatestVersion(1);
        t.setCreatedAt(now);
        t.setUpdatedAt(now);

        TemplateAggregate agg = new TemplateAggregate(t);
        TemplateVersion v1 = new TemplateVersion();
        v1.setId(UUID.randomUUID().toString());
        v1.setTemplateId(id);
        v1.setVersion(1);
        v1.setSchemaJson(schemaJson);
        v1.setChangeNote(changeNote != null ? changeNote : "初始版本");
        v1.setCreatedAt(now);
        agg.pendingNewVersion = v1;
        return agg;
    }

    /** 工厂：从持久化重建。 */
    public static TemplateAggregate reconstitute(Template persisted) {
        return new TemplateAggregate(persisted);
    }

    /** 发布（须有 schema，状态机校验）。 */
    public void publish(boolean hasSchema) {
        if (!hasSchema) {
            throw BusinessException.templateSchemaEmpty();
        }
        transitionTo(STATUS_PUBLISHED);
    }

    /** 归档。 */
    public void archive() {
        transitionTo(STATUS_ARCHIVED);
    }

    /** 推荐。 */
    public void feature() {
        transitionTo(STATUS_FEATURED);
    }

    /** 取消推荐（featured→published）。 */
    public void unfeature() {
        transitionTo(STATUS_PUBLISHED);
    }

    /**
     * schema 变更产新版本（latestVersion+1）。
     *
     * @return 待持久化的新 TemplateVersion（{@link #pendingNewVersion()} 拉取）
     */
    public TemplateVersion updateSchema(String schemaJson, String changeNote) {
        if (schemaJson == null || schemaJson.isBlank()) {
            throw BusinessException.templateSchemaEmpty();
        }
        int newVersion = root.getLatestVersion() + 1;
        TemplateVersion v = new TemplateVersion();
        v.setId(UUID.randomUUID().toString());
        v.setTemplateId(root.getId());
        v.setVersion(newVersion);
        v.setSchemaJson(schemaJson);
        v.setChangeNote(changeNote);
        v.setCreatedAt(Instant.now());
        root.setLatestVersion(newVersion);
        root.setUpdatedAt(Instant.now());
        this.pendingNewVersion = v;
        return v;
    }

    /**
     * 安装模板：校验 marketplace 可见 + 发布 TemplateInstalledEvent（跨聚合解耦）。
     *
     * <p>聚合根不直接创建 Page（跨聚合反模式）——发布事件由 TemplateInstallHandler 消费创建 page +
     * 写 installation 审计（pageId 同步可得）。
     *
     * @param schemaJson PageSchema JSON 字符串（G1 修复：领域事件不携带 Jackson JsonNode，
     *                   保持 domain 纯 POJO；Service 调用前已 toString）。
     */
    public TemplateAggregate install(String schemaJson, String siteId,
                                     String pageName, String resolvedPath, int version) {
        if (!isMarketplaceVisible(root.getStatus())) {
            throw BusinessException.templateNotPublished();
        }
        events.add(new TemplateInstalledEvent(
                root.getId(), siteId, pageName, resolvedPath, schemaJson, Instant.now()));
        return this;
    }

    public Template toTemplate() {
        return root;
    }

    /** 待持久化的新版本（newTemplate 初始版本 / updateSchema 新版本）。 */
    public TemplateVersion pendingNewVersion() {
        return pendingNewVersion;
    }

    /** 清除待持久化版本（Repository 持久化后调用）。 */
    public void clearPendingNewVersion() {
        this.pendingNewVersion = null;
    }

    public List<DomainEvent> pullEvents() {
        List<DomainEvent> drained = new ArrayList<>(events);
        events.clear();
        return drained;
    }

    private void transitionTo(String target) {
        String current = root.getStatus();
        if (!isValidTransition(current, target)) {
            throw BusinessException.templateInvalidStatusTransition(current, target);
        }
        root.setStatus(target);
        root.setUpdatedAt(Instant.now());
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

    // === 元数据更新（实例方法，内部校验） ===

    /**
     * 更新模板元数据（slug/name/category/description/thumbnail）。聚合根内部校验 slug 格式 + category 白名单，
     * 替代旧 Service 直接 mutate entity + 调静态校验器的贫血模式。
     */
    public void update(String slug, String name, String category, String description, String thumbnail) {
        validateSlug(slug);
        validateCategory(category);
        root.setSlug(slug);
        root.setName(name);
        root.setCategory(category);
        root.setDescription(description);
        root.setThumbnail(thumbnail);
        root.setUpdatedAt(Instant.now());
    }

    // === 无状态静态校验（合法纯函数；也供聚合根内部 + Service 边界校验复用） ===

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

    /** 模板是否对市场可见（published 或 featured）。 */
    public static boolean isMarketplaceVisible(String status) {
        return STATUS_PUBLISHED.equals(status) || STATUS_FEATURED.equals(status);
    }
}

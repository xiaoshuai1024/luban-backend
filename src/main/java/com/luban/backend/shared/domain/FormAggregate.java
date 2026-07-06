package com.luban.backend.shared.domain;

import com.luban.backend.shared.domain.event.DomainEvent;
import com.luban.backend.shared.entity.Form;
import com.luban.backend.shared.exception.BusinessException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 表单聚合根（backend-ddd-refactor plan v2 T8）。
 *
 * <p>封装 Form 域不变量（对齐 {@code .agents/rules/luban-engineering-principles.md}）：
 * <ul>
 *   <li><b>dedupPolicy 枚举白名单</b>：{@code reject}/{@code mark}/{@code overwrite}/{@code merge}
 *       （对齐 {@code DedupService.Policy}，DB 为 lowercase VARCHAR）</li>
 *   <li><b>status 枚举白名单</b>：{@code active}/{@code disabled}</li>
 *   <li><b>有线索不可删</b>：{@link #assertDeletable(boolean)} 断言决策
 *       （跨聚合查询 countByFormId 由 Service 完成后传入，聚合根零跨聚合依赖）</li>
 * </ul>
 *
 * <p>siteId/pageId 创建后不可变（多租户归属 + 页面归属）。
 * JSON 序列化（JsonNode→String）属 Service infra，聚合根只接收已序列化字符串。
 *
 * @see Form
 */
public final class FormAggregate {

    /** 允许的去重策略白名单（对齐 DedupService.Policy 的 lowercase 形式）。 */
    public static final Set<String> ALLOWED_DEDUP_POLICIES = Set.of("reject", "mark", "overwrite", "merge");
    /** 允许的状态白名单。 */
    public static final Set<String> ALLOWED_STATUSES = Set.of("active", "disabled");

    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_DISABLED = "disabled";
    public static final String DEFAULT_DEDUP_POLICY = "reject";
    public static final int DEFAULT_DEDUP_WINDOW_SECONDS = 86400;

    private final Form root;
    private final List<DomainEvent> events = new ArrayList<>();

    private FormAggregate(Form root) {
        this.root = root;
    }

    /**
     * 工厂：创建新表单（默认 dedupPolicy=reject、status=active、dedupWindow=86400）。
     *
     * @param id                表单 id
     * @param siteId            站点 id（不可变）
     * @param pageId            页面 id（不可变）
     * @param name              名称
     * @param fieldSchemaJson   字段 schema（已序列化）
     * @param submitConfigJson  提交配置（已序列化，null 兜底 "{}"）
     * @param dedupKeysJson     去重键（已序列化）
     * @param dedupWindow       去重窗口秒（null 兜底 86400）
     * @param dedupPolicy       去重策略（须 ∈ {@link #ALLOWED_DEDUP_POLICIES}，null/blank 兜底 reject）
     * @param antiSpamJson      反垃圾配置（已序列化）
     * @param status            状态（须 ∈ {@link #ALLOWED_STATUSES}，null 兜底 active）
     */
    public static FormAggregate newForm(String id, String siteId, String pageId, String name,
                                        String fieldSchemaJson, String submitConfigJson, String dedupKeysJson,
                                        Integer dedupWindow, String dedupPolicy, String antiSpamJson, String status) {
        String resolvedPolicy = (dedupPolicy != null && !dedupPolicy.isBlank()) ? dedupPolicy : DEFAULT_DEDUP_POLICY;
        validateDedupPolicy(resolvedPolicy);
        String resolvedStatus = status != null ? status : STATUS_ACTIVE;
        validateStatus(resolvedStatus);

        Instant now = Instant.now();
        Form f = new Form();
        f.setId(id);
        f.setSiteId(siteId);
        f.setPageId(pageId);
        f.setName(name);
        f.setFieldSchemaJson(fieldSchemaJson);
        f.setSubmitConfigJson(submitConfigJson != null ? submitConfigJson : "{}");
        f.setDedupKeysJson(dedupKeysJson);
        f.setDedupWindow(dedupWindow != null ? dedupWindow : DEFAULT_DEDUP_WINDOW_SECONDS);
        f.setDedupPolicy(resolvedPolicy);
        f.setAntiSpamJson(antiSpamJson);
        f.setStatus(resolvedStatus);
        f.setCreatedAt(now);
        f.setUpdatedAt(now);
        return new FormAggregate(f);
    }

    /** 工厂：从持久化重建。 */
    public static FormAggregate reconstitute(Form persisted) {
        return new FormAggregate(persisted);
    }

    /**
     * Patch 更新（null 字段保留原值）。dedupPolicy/status 非空时须过白名单。
     */
    public void update(String name, String fieldSchemaJson, String submitConfigJson, String dedupKeysJson,
                       Integer dedupWindow, String dedupPolicy, String antiSpamJson, String status) {
        if (dedupPolicy != null && !dedupPolicy.isBlank()) {
            validateDedupPolicy(dedupPolicy);
            root.setDedupPolicy(dedupPolicy);
        }
        if (status != null) {
            validateStatus(status);
            root.setStatus(status);
        }
        if (name != null) root.setName(name);
        if (fieldSchemaJson != null) root.setFieldSchemaJson(fieldSchemaJson);
        if (submitConfigJson != null) root.setSubmitConfigJson(submitConfigJson);
        if (dedupKeysJson != null) root.setDedupKeysJson(dedupKeysJson);
        if (dedupWindow != null) root.setDedupWindow(dedupWindow);
        if (antiSpamJson != null) root.setAntiSpamJson(antiSpamJson);
        root.setUpdatedAt(Instant.now());
    }

    /** 禁用表单（active→disabled；已 disabled 幂等 no-op）。 */
    public void disable() {
        if (STATUS_DISABLED.equals(root.getStatus())) return;   // 幂等
        if (!STATUS_ACTIVE.equals(root.getStatus())) {
            throw BusinessException.invalidStateTransition(root.getStatus(), STATUS_DISABLED);
        }
        root.setStatus(STATUS_DISABLED);
        root.setUpdatedAt(Instant.now());
    }

    /** 启用表单（disabled→active；已 active 幂等 no-op）。 */
    public void enable() {
        if (STATUS_ACTIVE.equals(root.getStatus())) return;   // 幂等
        if (!STATUS_DISABLED.equals(root.getStatus())) {
            throw BusinessException.invalidStateTransition(root.getStatus(), STATUS_ACTIVE);
        }
        root.setStatus(STATUS_ACTIVE);
        root.setUpdatedAt(Instant.now());
    }

    /**
     * 断言可删除：有线索则抛 FORM_HAS_LEADS。
     *
     * <p>跨聚合查询（{@code leadMapper.countByFormId}）由 Application Service 完成后传入 boolean，
     * 聚合根零跨聚合 Mapper 依赖（DDD：聚合根不直接调 Lead 域）。聚合根负责"是否可删"的决策断言。
     *
     * @param hasLeads 是否已有关联线索（Service 查询后传入）
     */
    public void assertDeletable(boolean hasLeads) {
        if (hasLeads) {
            throw BusinessException.formHasLeads();
        }
    }

    public Form toEntity() {
        return root;
    }

    public List<DomainEvent> pullEvents() {
        List<DomainEvent> drained = new ArrayList<>(events);
        events.clear();
        return drained;
    }

    private static void validateDedupPolicy(String policy) {
        if (!ALLOWED_DEDUP_POLICIES.contains(policy)) {
            throw BusinessException.invalidArgument(
                    "dedupPolicy must be one of " + ALLOWED_DEDUP_POLICIES);
        }
    }

    private static void validateStatus(String status) {
        if (!ALLOWED_STATUSES.contains(status)) {
            throw BusinessException.invalidArgument(
                    "status must be one of " + ALLOWED_STATUSES);
        }
    }
}

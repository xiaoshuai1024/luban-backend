package com.luban.backend.shared.domain;

import com.luban.backend.shared.domain.event.DomainEvent;
import com.luban.backend.shared.domain.event.PagePublishedEvent;
import com.luban.backend.shared.domain.event.PageUnpublishedEvent;
import com.luban.backend.shared.entity.Page;
import com.luban.backend.shared.entity.PublishedPage;
import com.luban.backend.shared.exception.BusinessException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Page 聚合根（backend-ddd-refactor plan v2 T6）。
 *
 * <p>封装 Page 域不变量（对齐 {@code .agents/rules/luban-engineering-principles.md}）：
 * <ul>
 *   <li><b>状态机</b>：draft→published→archived（draft→archived 允许直跳归档，
 *       published↔archived 允许下线/重发，published→published 幂等 no-op）</li>
 *   <li><b>双写一致性</b>：PUT published ≡ POST /publish——{@link #publish(String)} 统一入口，
 *       发 PagePublishedEvent + {@link #buildPublishedSnapshot()} 产快照（含真实 actor）</li>
 *   <li><b>修复生产级问题</b>：旧 syncPublishedState 的 publishedBy=null 不一致，聚合根统一用真实 actor；
 *       publish 快照失败不再用 System.err 吞，由 Service 用 Logger 记录</li>
 * </ul>
 *
 * <p>草稿/发布隔离：已发布页经 PUT 改草稿内容不刷新 published_pages 快照——这是设计意图
 * （公开内容稳定直到显式重发），由 Service 在 syncPublishedState 仅在状态转换时调用聚合根方法保证。
 *
 * @see Page
 * @see PublishedPage
 */
public final class PageAggregate {

    public static final String STATUS_DRAFT = "draft";
    public static final String STATUS_PUBLISHED = "published";
    public static final String STATUS_ARCHIVED = "archived";
    public static final Set<String> VALID_STATUSES = Set.of(STATUS_DRAFT, STATUS_PUBLISHED, STATUS_ARCHIVED);

    private final Page root;
    private final List<DomainEvent> events = new ArrayList<>();

    private PageAggregate(Page root) {
        this.root = root;
    }

    /** 工厂：创建新页面（默认 status=draft）。 */
    public static PageAggregate newPage(String id, String siteId, String name, String path,
                                        String schemaJson, String seoJson) {
        Instant now = Instant.now();
        Page p = new Page();
        p.setId(id);
        p.setSiteId(siteId);
        p.setName(name);
        p.setPath(path);
        p.setStatus(STATUS_DRAFT);
        p.setSchemaJson(schemaJson != null ? schemaJson : "{}");
        p.setSeoJson(seoJson);
        p.setCreatedAt(now);
        p.setUpdatedAt(now);
        return new PageAggregate(p);
    }

    /** 工厂：从持久化重建。 */
    public static PageAggregate reconstitute(Page persisted) {
        return new PageAggregate(persisted);
    }

    /** Patch 更新页面字段（null 保留原值）。 */
    public void update(String name, String path, String schemaJson, String seoJson) {
        if (name != null) root.setName(name);
        if (path != null) root.setPath(path);
        if (schemaJson != null) root.setSchemaJson(schemaJson);
        if (seoJson != null) root.setSeoJson(seoJson);
        root.setUpdatedAt(Instant.now());
    }

    /**
     * 发布页面（draft/published→published，幂等）。
     *
     * @param publishedBy 发布人（真实 actor，修复旧 syncPublishedState 的 null 不一致）
     */
    public void publish(String publishedBy) {
        // 幂等：已 published 不重复发事件（兼容 PUT published→published no-op）
        if (STATUS_PUBLISHED.equals(root.getStatus())) {
            return;
        }
        // 状态机：archived→published 允许（重新发布），draft→published 允许
        if (!isValidPublishTransition(root.getStatus())) {
            throw BusinessException.invalidStateTransition(root.getStatus(), STATUS_PUBLISHED);
        }
        Instant now = Instant.now();
        root.setStatus(STATUS_PUBLISHED);
        root.setPublishedAt(now);
        root.setPublishedBy(publishedBy);
        root.setUpdatedAt(now);
        events.add(new PagePublishedEvent(root.getId(), root.getSiteId(), root.getPath(), now));
    }

    /** 下线页面（published→archived）。draft 不可下线。 */
    public void unpublish() {
        if (!STATUS_PUBLISHED.equals(root.getStatus())) {
            throw BusinessException.invalidStateTransition(root.getStatus(), STATUS_ARCHIVED);
        }
        Instant now = Instant.now();
        root.setStatus(STATUS_ARCHIVED);
        root.setUpdatedAt(now);
        events.add(new PageUnpublishedEvent(root.getId(), root.getSiteId(), now));
    }

    /** 归档（draft/published→archived，软删除/下线）。 */
    public void archive() {
        if (STATUS_ARCHIVED.equals(root.getStatus())) {
            return;   // 幂等
        }
        if (!isValidArchiveTransition(root.getStatus())) {
            throw BusinessException.invalidStateTransition(root.getStatus(), STATUS_ARCHIVED);
        }
        Instant now = Instant.now();
        // G1 修复 S3：published→archived 与 unpublish() 语义相同（下线），
        // 一致发 PageUnpublishedEvent（让 PageService/Projection 清理 published_pages 快照）。
        boolean wasPublished = STATUS_PUBLISHED.equals(root.getStatus());
        root.setStatus(STATUS_ARCHIVED);
        root.setUpdatedAt(now);
        if (wasPublished) {
            events.add(new PageUnpublishedEvent(root.getId(), root.getSiteId(), now));
        }
    }

    /**
     * 构建发布快照（publish 后由 Service 持久化到 published_pages）。
     *
     * <p>快照字段对齐聚合根当前状态，<b>publishedBy 必为真实 actor</b>（修复旧不一致）。
     */
    public PublishedPage buildPublishedSnapshot() {
        PublishedPage snapshot = new PublishedPage();
        snapshot.setId(UUID.randomUUID().toString());
        snapshot.setPageId(root.getId());
        snapshot.setSiteId(root.getSiteId());
        snapshot.setName(root.getName());
        snapshot.setPath(root.getPath());
        snapshot.setSchemaJson(root.getSchemaJson());
        snapshot.setSeoJson(root.getSeoJson());
        snapshot.setPublishedAt(root.getPublishedAt());
        snapshot.setPublishedBy(root.getPublishedBy());
        return snapshot;
    }

    public Page toPage() {
        return root;
    }

    public List<DomainEvent> pullEvents() {
        List<DomainEvent> drained = new ArrayList<>(events);
        events.clear();
        return drained;
    }

    private static boolean isValidPublishTransition(String from) {
        return STATUS_DRAFT.equals(from) || STATUS_ARCHIVED.equals(from);
    }

    private static boolean isValidArchiveTransition(String from) {
        return STATUS_DRAFT.equals(from) || STATUS_PUBLISHED.equals(from);
    }
}

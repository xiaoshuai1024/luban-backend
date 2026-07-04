package com.luban.backend.shared.domain;

import com.luban.backend.shared.domain.event.DomainEvent;
import com.luban.backend.shared.domain.event.SiteDeletedEvent;
import com.luban.backend.shared.entity.Site;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Site 聚合根（backend-ddd-refactor plan v2 T5）。
 *
 * <p>封装 Site 域不变量（对齐 {@code .agents/rules/luban-engineering-principles.md}）：
 * <ul>
 *   <li><b>7 表级联删除事务边界</b>：删除须按 FK 顺序清 leads/forms/datasources/collections/
 *       channels/campaigns/pages 7 子表，最后删 site 行，全部在一个事务内
 *       （实际 Mapper 调用由 {@code SiteRepositoryImpl} 按 FK 顺序执行，聚合根定义删除事件 +
 *       顺序契约）。channels 须先于 campaigns（FK channels→campaigns）；pages 须先于 site
 *       （page_versions 经 FK CASCADE 自动清）</li>
 *   <li>工厂 newSite：默认 status=active</li>
 * </ul>
 *
 * @see Site
 */
public final class SiteAggregate {

    public static final String STATUS_ACTIVE = "active";

    private final Site root;
    private final List<DomainEvent> events = new ArrayList<>();

    private SiteAggregate(Site root) {
        this.root = root;
    }

    /** 工厂：创建新站点（默认 status=active）。 */
    public static SiteAggregate newSite(String id, String name, String slug, String baseUrl) {
        Instant now = Instant.now();
        Site s = new Site();
        s.setId(id);
        s.setName(name);
        s.setSlug(slug);
        s.setBaseUrl(baseUrl);
        s.setStatus(STATUS_ACTIVE);
        s.setCreatedAt(now);
        s.setUpdatedAt(now);
        return new SiteAggregate(s);
    }

    /** 工厂：从持久化重建。 */
    public static SiteAggregate reconstitute(Site persisted) {
        return new SiteAggregate(persisted);
    }

    /** Patch 更新站点（null 字段保留原值）。 */
    public void update(String name, String baseUrl, String seoJson) {
        if (name != null) root.setName(name);
        if (baseUrl != null) root.setBaseUrl(baseUrl);
        if (seoJson != null) root.setSeoJson(seoJson);
        root.setUpdatedAt(Instant.now());
    }

    /**
     * 标记删除 + 发 SiteDeletedEvent。
     *
     * <p>实际 7 表级联删除由 SiteRepositoryImpl.delete() 按 FK 顺序执行：
     * leads → forms → datasources → collections → channels → campaigns → pages → site。
     * 聚合根负责发事件（供 Analytics/Campaign 等清理关联数据）。
     */
    public void delete() {
        events.add(new SiteDeletedEvent(root.getId(), Instant.now()));
    }

    public Site toSite() {
        return root;
    }

    public List<DomainEvent> pullEvents() {
        List<DomainEvent> drained = new ArrayList<>(events);
        events.clear();
        return drained;
    }
}

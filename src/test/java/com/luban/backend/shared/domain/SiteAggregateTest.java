package com.luban.backend.shared.domain;

import com.luban.backend.shared.entity.Site;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SiteAggregate 单测（backend-ddd-refactor plan v2 T5）。
 *
 * <p>锁定真聚合根范式不变量：
 * <ul>
 *   <li>工厂 newSite：默认 status=active</li>
 *   <li>工厂 reconstitute：从持久化重建</li>
 *   <li>delete：标记删除 + 发 SiteDeletedEvent（实际 7 表级联删除由 Repository 按 FK 顺序执行）</li>
 *   <li>update：patch 语义</li>
 * </ul>
 *
 * <p>级联删除事务边界：聚合根定义"删除事件 + 顺序契约"，RepositoryImpl 按 FK 顺序清 7 子表。
 */
class SiteAggregateTest {

    @Test
    void newSiteDefaultsToActiveStatus() {
        Instant before = Instant.now();
        SiteAggregate agg = SiteAggregate.newSite("s-1", "我的站点", "my-site", null);

        Site s = agg.toSite();
        assertThat(s.getId()).isEqualTo("s-1");
        assertThat(s.getStatus()).isEqualTo("active");
        assertThat(s.getCreatedAt()).isBetween(before, Instant.now().plusSeconds(1));
        assertThat(agg.pullEvents()).isEmpty();
    }

    @Test
    void reconstitutePreservesState() {
        Instant created = Instant.parse("2026-01-01T00:00:00Z");
        Site persisted = new Site();
        persisted.setId("s-9");
        persisted.setName("旧站");
        persisted.setSlug("old");
        persisted.setStatus("active");
        persisted.setCreatedAt(created);

        SiteAggregate agg = SiteAggregate.reconstitute(persisted);

        assertThat(agg.toSite().getName()).isEqualTo("旧站");
        assertThat(agg.pullEvents()).isEmpty();
    }

    @Test
    void updatePatchesFields() {
        SiteAggregate agg = SiteAggregate.newSite("s-1", "原名", "slug", null);

        agg.update("新名", "https://new.example.com", null);

        Site s = agg.toSite();
        assertThat(s.getName()).isEqualTo("新名");
        assertThat(s.getBaseUrl()).isEqualTo("https://new.example.com");
        assertThat(s.getSlug()).isEqualTo("slug");   // 保留
    }

    @Test
    void deleteMarksForDeletionAndPublishesEvent() {
        SiteAggregate agg = SiteAggregate.newSite("s-1", "站点", "slug", null);

        agg.delete();

        // 发 SiteDeletedEvent（供 Analytics/Campaign 等清理关联数据）
        var events = agg.pullEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(com.luban.backend.shared.domain.event.SiteDeletedEvent.class);
        com.luban.backend.shared.domain.event.SiteDeletedEvent ev =
                (com.luban.backend.shared.domain.event.SiteDeletedEvent) events.get(0);
        assertThat(ev.siteId()).isEqualTo("s-1");
        assertThat(events).hasSize(1);
    }

    @Test
    void pullEventsDrains() {
        SiteAggregate agg = SiteAggregate.newSite("s-1", "站点", "slug", null);
        agg.delete();
        assertThat(agg.pullEvents()).hasSize(1);
        assertThat(agg.pullEvents()).isEmpty();
    }
}

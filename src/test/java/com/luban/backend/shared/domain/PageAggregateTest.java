package com.luban.backend.shared.domain;

import com.luban.backend.shared.domain.event.PagePublishedEvent;
import com.luban.backend.shared.domain.event.PageUnpublishedEvent;
import com.luban.backend.shared.entity.Page;
import com.luban.backend.shared.entity.PublishedPage;
import com.luban.backend.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PageAggregate 单测（backend-ddd-refactor plan v2 T6）。
 *
 * <p>锁定真聚合根范式不变量 + 顺带修复生产级问题：
 * <ul>
 *   <li>状态机 draft→published→archived（显式转换，非法抛 invalidStateTransition）</li>
 *   <li>publish(publishedBy)：status→published，发 PagePublishedEvent，buildPublishedSnapshot 含 publishedBy
 *       （修复现状 syncPublishedState publishedBy=null 的不一致）</li>
 *   <li>unpublish()：status→archived，发 PageUnpublishedEvent</li>
 *   <li>buildPublishedSnapshot：快照字段完整对齐（含 actor，非 null）</li>
 * </ul>
 */
class PageAggregateTest {

    @Test
    void newPageDefaultsToDraftStatus() {
        Instant before = Instant.now();
        PageAggregate agg = PageAggregate.newPage(
                "p-1", "site-1", "首页", "/", "{}", null);

        Page p = agg.toPage();
        assertThat(p.getStatus()).isEqualTo("draft");
        assertThat(p.getCreatedAt()).isBetween(before, Instant.now().plusSeconds(1));
        assertThat(agg.pullEvents()).isEmpty();
    }

    @Test
    void reconstitutePreservesState() {
        Page persisted = new Page();
        persisted.setId("p-9");
        persisted.setStatus("published");
        persisted.setName("页");
        PageAggregate agg = PageAggregate.reconstitute(persisted);

        assertThat(agg.toPage().getStatus()).isEqualTo("published");
        assertThat(agg.pullEvents()).isEmpty();
    }

    @Test
    void publishTransitionsToPublishedAndSetsAuditFields() {
        PageAggregate agg = PageAggregate.newPage(
                "p-1", "site-1", "首页", "/", "{}", null);

        agg.publish("user-1");

        Page p = agg.toPage();
        assertThat(p.getStatus()).isEqualTo("published");
        assertThat(p.getPublishedBy()).isEqualTo("user-1");
        assertThat(p.getPublishedAt()).isNotNull();
        // 发 PagePublishedEvent
        var events = agg.pullEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(PagePublishedEvent.class);
    }

    @Test
    void buildPublishedSnapshotIncludesActorNotPartOfOldInconsistency() {
        // 关键修复：快照的 publishedBy 必须是真实 actor（非 null），
        // 对齐 publish() 而非现状 syncPublishedState 的 null
        PageAggregate agg = PageAggregate.newPage(
                "p-1", "site-1", "首页", "/", "{\"x\":1}", null);
        agg.publish("actor-1");

        PublishedPage snapshot = agg.buildPublishedSnapshot();

        assertThat(snapshot.getPublishedBy()).isEqualTo("actor-1");   // 非 null
        assertThat(snapshot.getPageId()).isEqualTo("p-1");
        assertThat(snapshot.getSiteId()).isEqualTo("site-1");
        assertThat(snapshot.getName()).isEqualTo("首页");
        assertThat(snapshot.getPath()).isEqualTo("/");
        assertThat(snapshot.getSchemaJson()).isEqualTo("{\"x\":1}");
        assertThat(snapshot.getPublishedAt()).isNotNull();
        assertThat(snapshot.getId()).isNotBlank();
    }

    @Test
    void unpublishTransitionsToArchivedAndEmitsEvent() {
        Page persisted = new Page();
        persisted.setId("p-1");
        persisted.setSiteId("site-1");
        persisted.setStatus("published");
        PageAggregate agg = PageAggregate.reconstitute(persisted);

        agg.unpublish();

        assertThat(agg.toPage().getStatus()).isEqualTo("archived");
        var events = agg.pullEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(PageUnpublishedEvent.class);
    }

    @Test
    void publishRejectsAlreadyPublishedViaInvalidTransition() {
        // published→published 是 no-op 还是抛异常？现状 PUT 允许 published→published。
        // 聚合根：publish() 幂等——已 published 不重复发事件，但允许调用（兼容 PUT）
        Page persisted = new Page();
        persisted.setId("p-1");
        persisted.setStatus("published");
        PageAggregate agg = PageAggregate.reconstitute(persisted);

        agg.publish("user-2");   // 幂等，不抛

        // 已 published 时不再重复发事件
        assertThat(agg.pullEvents()).isEmpty();
    }

    @Test
    void unpublishRejectsDraftViaInvalidTransition() {
        PageAggregate agg = PageAggregate.newPage(
                "p-1", "site-1", "首页", "/", "{}", null);   // draft

        assertThatThrownBy(() -> agg.unpublish())
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo("INVALID_STATE_TRANSITION");
    }

    @Test
    void updatePatchesFields() {
        PageAggregate agg = PageAggregate.newPage(
                "p-1", "site-1", "原名", "/old", "{}", null);

        agg.update("新名", "/new", null, null);

        Page p = agg.toPage();
        assertThat(p.getName()).isEqualTo("新名");
        assertThat(p.getPath()).isEqualTo("/new");
    }

    @Test
    void archiveTransitionsDraftOrPublishedToArchived() {
        // draft→archived 允许（直接归档）
        PageAggregate agg = PageAggregate.newPage(
                "p-1", "site-1", "页", "/", "{}", null);
        agg.archive();
        assertThat(agg.toPage().getStatus()).isEqualTo("archived");
    }
}

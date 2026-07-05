package com.luban.backend.shared.domain.event;

import java.time.Instant;

/**
 * 站点删除事件（backend-ddd-refactor plan v2 T4）。
 *
 * <p>由 {@code SiteAggregate.delete()} 发布。SiteAggregate 的 delete 封装 7 子表级联删除不变量，
 * 删除完成后发布此事件，供其他聚合（Analytics/Campaign 等）清理关联数据。
 *
 * <p><b>⚠️ 当前状态：预留扩展点（孤儿事件）</b>。
 * 截至 2026-07-05，本事件无任何 {@code @TransactionalEventListener} 消费者
 * （全仓 grep 确认）。7 子表级联删除由 {@code SiteCascadeDeleter.deleteChildren}
 * 在主事务内同步完成（{@code SiteService.delete}），本事件本身**不触发任何副作用**。
 *
 * <p><b>设计意图</b>：保留此事件为未来跨聚合清理（如 Analytics 事件归档、Campaign 软删）
 * 留扩展点。新增消费者时在 {@code operatorside/eventhandler/} 实现
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT) void on(SiteDeletedEvent e)}。
 *
 * <p><b>不要删除本事件</b>：删除会破坏未来扩展点；且 {@code SiteAggregateTest.delete()}
 * 已断言事件被发出，删除需同步改测试。如确认永远不会跨聚合并发，再考虑清理。
 *
 * @param siteId     被删除的站点 id
 * @param occurredAt 发生时间
 */
public record SiteDeletedEvent(
        String siteId,
        Instant occurredAt
) implements DomainEvent {
    @Override
    public String aggregateId() { return siteId; }
}

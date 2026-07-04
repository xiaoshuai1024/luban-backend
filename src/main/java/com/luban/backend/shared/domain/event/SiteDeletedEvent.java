package com.luban.backend.shared.domain.event;

import java.time.Instant;

/**
 * 站点删除事件（backend-ddd-refactor plan v2 T4）。
 *
 * <p>由 {@code SiteAggregate.delete()} 发布。SiteAggregate 的 delete 封装 7 子表级联删除不变量，
 * 删除完成后发布此事件，供其他聚合（Analytics/Campaign 等）清理关联数据。
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

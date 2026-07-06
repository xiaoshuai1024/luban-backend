package com.luban.backend.shared.domain.event;

import java.time.Instant;

/**
 * 页面取消发布事件（backend-ddd-refactor plan v2 T4）。
 *
 * <p>由 {@code PageAggregate.unpublish()} 发布，{@code PagePublishSideEffectHandler} 消费
 * （触发 published_pages 删除/缓存清理）。
 *
 * @param pageId     页面 id
 * @param siteId     站点 id
 * @param occurredAt 发生时间
 */
public record PageUnpublishedEvent(
        String pageId,
        String siteId,
        Instant occurredAt
) implements DomainEvent {
    @Override
    public String aggregateId() { return pageId; }
}

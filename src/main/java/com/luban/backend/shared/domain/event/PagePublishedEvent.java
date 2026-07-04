package com.luban.backend.shared.domain.event;

import java.time.Instant;

/**
 * 页面发布事件（backend-ddd-refactor plan v2 T4）。
 *
 * <p>由 {@code PageAggregate.publish()} 发布，{@code PagePublishSideEffectHandler} 消费
 * （触发短链刷新/SEO 缓存失效等副作用）。
 *
 * @param pageId     页面 id
 * @param siteId     站点 id
 * @param path       页面路径
 * @param occurredAt 发生时间
 */
public record PagePublishedEvent(
        String pageId,
        String siteId,
        String path,
        Instant occurredAt
) implements DomainEvent {
    @Override
    public String aggregateId() { return pageId; }
}

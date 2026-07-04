package com.luban.backend.shared.domain.event;

import java.time.Instant;

/**
 * 线索转化事件（backend-ddd-refactor plan v2 T4）。
 *
 * <p>由 {@code LeadAggregate.convert()} 发布，{@code AnalyticsAttributionHandler} 消费
 * （用于转化归因统计）。
 *
 * @param leadId      线索 id
 * @param siteId      站点 id
 * @param convertedAt 转化时间
 * @param occurredAt  事件发生时间
 */
public record LeadConvertedEvent(
        String leadId,
        String siteId,
        Instant convertedAt,
        Instant occurredAt
) implements DomainEvent {
    @Override
    public String aggregateId() { return leadId; }
}

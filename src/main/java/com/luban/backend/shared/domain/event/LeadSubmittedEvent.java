package com.luban.backend.shared.domain.event;

import java.time.Instant;

/**
 * 留资提交事件（backend-ddd-refactor plan v2 T4）。
 *
 * <p>由 {@code LeadAggregate.submit()} 发布，{@code LeadNotifyHandler} 消费
 * （替代当前 LeadService 的 TransactionSynchronizationManager afterCommit 手动注册）。
 *
 * @param leadId    线索 id
 * @param formId    表单 id
 * @param siteId    站点 id
 * @param occurredAt 发生时间
 */
public record LeadSubmittedEvent(
        String leadId,
        String formId,
        String siteId,
        Instant occurredAt
) implements DomainEvent {
    @Override
    public String aggregateId() { return leadId; }
}

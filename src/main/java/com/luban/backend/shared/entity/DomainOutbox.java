package com.luban.backend.shared.entity;

import java.time.Instant;

/**
 * domain_outbox 表实体（at-least-once 事件投递保障）。
 *
 * <p>Service 在聚合根 pullEvents() 后同事务内插入；OutboxRelayScheduler 扫描
 * processed_at IS NULL 的记录补偿投递。成功 markProcessed，失败 incrementAttempts + 退避。
 */
public class DomainOutbox {
    private String id;
    private String aggregateId;
    private String eventType;
    private String payloadJson;
    private Instant occurredAt;
    private Instant processedAt;
    private int attempts;
    private Instant nextRetryAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getAggregateId() { return aggregateId; }
    public void setAggregateId(String aggregateId) { this.aggregateId = aggregateId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }

    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }

    public Instant getProcessedAt() { return processedAt; }
    public void setProcessedAt(Instant processedAt) { this.processedAt = processedAt; }

    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }

    public Instant getNextRetryAt() { return nextRetryAt; }
    public void setNextRetryAt(Instant nextRetryAt) { this.nextRetryAt = nextRetryAt; }
}

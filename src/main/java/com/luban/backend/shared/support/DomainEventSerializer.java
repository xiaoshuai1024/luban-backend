package com.luban.backend.shared.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.luban.backend.shared.domain.event.DomainEvent;
import com.luban.backend.shared.entity.DomainOutbox;
import com.luban.backend.shared.util.JsonUtil;

import java.time.Instant;
import java.util.UUID;

/**
 * 领域事件 ↔ outbox 行 序列化器（at-least-once 投递）。
 *
 * <p>使用 Jackson 序列化事件 record 为 payload_json。event_type 取简单类名
 * （如 LeadConvertedEvent），{@link DomainEventRegistry} 负责反序列化时的类型解析。
 *
 * <p>Instant 序列化为 epoch 毫秒（Jackson JavaTimeModule 默认 ISO-8601 字符串）——
 * 为避免引入额外模块依赖，payload 内 Instant 字段由 Jackson 默认 ISO-8601 处理
 * （record 的 Instant 字段经 ObjectMapper 写为字符串，反序列化对称还原）。
 */
public final class DomainEventSerializer {

    private DomainEventSerializer() {}

    /** DomainEvent → DomainOutbox 行（未处理，nextRetryAt=occurredAt 立即可投递）。 */
    public static DomainOutbox toOutboxRow(DomainEvent event) {
        DomainOutbox row = new DomainOutbox();
        row.setId(UUID.randomUUID().toString());
        row.setAggregateId(event.aggregateId());
        row.setEventType(event.getClass().getSimpleName());
        row.setPayloadJson(JsonUtil.writeValueAsString(event));
        row.setOccurredAt(event.occurredAt());
        row.setProcessedAt(null);
        row.setAttempts(0);
        row.setNextRetryAt(event.occurredAt());
        return row;
    }

    /** 校验 payload 可序列化（非 null 即合法）。供 Service 双写前预检。 */
    public static boolean isSerializable(DomainEvent event) {
        return JsonUtil.writeValueAsString(event) != null;
    }
}

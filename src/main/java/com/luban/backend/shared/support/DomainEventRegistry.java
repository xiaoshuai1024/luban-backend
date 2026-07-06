package com.luban.backend.shared.support;

import com.luban.backend.shared.domain.event.CampaignActivatedEvent;
import com.luban.backend.shared.domain.event.DomainEvent;
import com.luban.backend.shared.domain.event.ExperimentEndedEvent;
import com.luban.backend.shared.domain.event.ExperimentStartedEvent;
import com.luban.backend.shared.domain.event.LeadConvertedEvent;
import com.luban.backend.shared.domain.event.LeadSubmittedEvent;
import com.luban.backend.shared.domain.event.PagePublishedEvent;
import com.luban.backend.shared.domain.event.PageUnpublishedEvent;
import com.luban.backend.shared.domain.event.SiteDeletedEvent;
import com.luban.backend.shared.domain.event.SubscriptionExpiredEvent;
import com.luban.backend.shared.domain.event.SubscriptionUpgradedEvent;
import com.luban.backend.shared.domain.event.TemplateInstalledEvent;
import com.luban.backend.shared.util.JsonUtil;

import java.util.Map;

/**
 * 领域事件类型注册表（outbox 反序列化用）。
 *
 * <p>event_type（简单类名）→ Class 映射。新增领域事件时需在此注册一行。
 * OutboxRelayScheduler 从 outbox 行的 event_type + payload_json 重建 DomainEvent。
 *
 * <p>注册表是显式枚举（非反射扫描）——可读、可审计、启动快、无类路径扫描副作用。
 */
public final class DomainEventRegistry {

    private static final Map<String, Class<? extends DomainEvent>> REGISTRY = Map.ofEntries(
            Map.entry("CampaignActivatedEvent", CampaignActivatedEvent.class),
            Map.entry("ExperimentEndedEvent", ExperimentEndedEvent.class),
            Map.entry("ExperimentStartedEvent", ExperimentStartedEvent.class),
            Map.entry("LeadConvertedEvent", LeadConvertedEvent.class),
            Map.entry("LeadSubmittedEvent", LeadSubmittedEvent.class),
            Map.entry("PagePublishedEvent", PagePublishedEvent.class),
            Map.entry("PageUnpublishedEvent", PageUnpublishedEvent.class),
            Map.entry("SiteDeletedEvent", SiteDeletedEvent.class),
            Map.entry("SubscriptionExpiredEvent", SubscriptionExpiredEvent.class),
            Map.entry("SubscriptionUpgradedEvent", SubscriptionUpgradedEvent.class),
            Map.entry("TemplateInstalledEvent", TemplateInstalledEvent.class)
    );

    private DomainEventRegistry() {}

    /** 按 event_type 反序列化 payload_json 为 DomainEvent；未知类型返回 null。 */
    public static DomainEvent deserialize(String eventType, String payloadJson) {
        Class<? extends DomainEvent> clazz = REGISTRY.get(eventType);
        if (clazz == null) return null;
        try {
            return JsonUtil.MAPPER.readValue(payloadJson, clazz);
        } catch (Exception e) {
            return null;
        }
    }
}

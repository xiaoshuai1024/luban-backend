package com.luban.backend.shared.domain.event;

import java.time.Instant;

/**
 * 套餐升级事件（backend-ddd-refactor plan v2 T4）。
 *
 * <p>由 {@code SubscriptionAggregate.subscribe()} 发布，{@code SubscriptionDowngradeHandler} 消费
 * （触发 FeatureGate 升级、用量配额重置等副作用）。为 Plan B（付费续费）预留。
 *
 * @param userId     用户 id
 * @param fromPlan   升级前套餐
 * @param toPlan     升级后套餐
 * @param occurredAt 发生时间
 */
public record SubscriptionUpgradedEvent(
        String userId,
        String fromPlan,
        String toPlan,
        Instant occurredAt
) implements DomainEvent {
    @Override
    public String aggregateId() { return userId; }
}

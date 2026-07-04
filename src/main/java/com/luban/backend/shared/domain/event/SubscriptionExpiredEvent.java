package com.luban.backend.shared.domain.event;

import java.time.Instant;

/**
 * 订阅/试用到期降级事件（backend-ddd-refactor plan v2 T4）。
 *
 * <p>由 {@code SubscriptionAggregate.expireTrial()} 发布，{@code SubscriptionDowngradeHandler} 消费
 * （触发 FeatureGate 降级、用户通知等副作用）。
 *
 * @param userId     用户 id
 * @param fromPlan   降级前套餐
 * @param toPlan     降级后套餐（通常 free）
 * @param occurredAt 发生时间
 */
public record SubscriptionExpiredEvent(
        String userId,
        String fromPlan,
        String toPlan,
        Instant occurredAt
) implements DomainEvent {
    @Override
    public String aggregateId() { return userId; }
}

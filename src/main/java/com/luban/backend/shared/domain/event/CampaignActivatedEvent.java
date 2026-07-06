package com.luban.backend.shared.domain.event;

import java.time.Instant;

/**
 * 活动激活/完成/取消事件（backend-ddd-refactor plan v2 T4 / G1 补）。
 *
 * <p>由 {@code CampaignAggregate.transition()} 在 planned→active / active→completed / active→cancelled /
 * planned→cancelled 时发布。当前由 {@code CampaignLifecycleHandler} 消费（日志占位 + 可观测），
 * 未来真实消费者（渠道下线、analytics 归因窗口、订阅计费）接入即可。
 *
 * @param campaignId 活动 id
 * @param siteId     站点 id
 * @param fromStatus 转换前状态
 * @param toStatus   转换后状态
 * @param occurredAt 发生时间
 */
public record CampaignActivatedEvent(
        String campaignId,
        String siteId,
        String fromStatus,
        String toStatus,
        Instant occurredAt
) implements DomainEvent {
    @Override
    public String aggregateId() { return campaignId; }
}

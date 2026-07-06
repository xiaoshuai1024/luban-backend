package com.luban.backend.shared.domain.event;

import java.time.Instant;

/**
 * 实验结束事件（backend-ddd-refactor plan v2 T4 / G1 补）。
 *
 * <p>由 {@code AbExperimentAggregate.end()} 发布。当前由 {@code ExperimentLifecycleHandler}
 * 消费（日志占位 + 可观测），未来真实消费者接入即可。
 *
 * @param experimentId 实验 id
 * @param siteId       站点 id
 * @param pageId       页面 id
 * @param occurredAt   发生时间
 */
public record ExperimentEndedEvent(
        String experimentId,
        String siteId,
        String pageId,
        Instant occurredAt
) implements DomainEvent {
    @Override
    public String aggregateId() { return experimentId; }
}

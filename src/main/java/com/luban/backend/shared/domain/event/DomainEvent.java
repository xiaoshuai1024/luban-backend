package com.luban.backend.shared.domain.event;

import java.time.Instant;

/**
 * 领域事件标记接口（backend-ddd-refactor plan v2 T4）。
 *
 * <p>所有领域事件实现此接口。事件由聚合根产生（{@code events.add(new XxxEvent(...))}），
 * 由 Application Service 在事务提交后通过 {@link org.springframework.context.ApplicationEventPublisher}
 * 发布，由 {@link org.springframework.transaction.event.TransactionalEventListener}（AFTER_COMMIT）消费。
 *
 * <p>事件是<b>不可变</b>的（推荐用 record 实现），携带聚合 id + 发生时间 + 业务数据。
 * 对齐 DDD：聚合间通过事件解耦，禁止聚合 A 直接调用聚合 B 的 Service。
 *
 * @see org.springframework.transaction.event.TransactionalEventListener
 */
public interface DomainEvent {
    /** 事件发生时间（UTC）。 */
    Instant occurredAt();
    /** 发布该事件的聚合根 id。 */
    String aggregateId();
}

package com.luban.backend.shared.domain.event;

import java.time.Instant;

/**
 * 领域事件标记接口（backend-ddd-refactor plan v2 T4）。
 *
 * <p>所有领域事件实现此接口。事件由聚合根产生（{@code events.add(new XxxEvent(...))}），
 * 由 Application Service 在事务提交后发布（基础设施层负责事件投递机制，本接口不感知），
 * 由事件监听器在事务提交后（AFTER_COMMIT）消费。
 *
 * <p>事件是<b>不可变</b>的（推荐用 record 实现），携带聚合 id + 发生时间 + 业务数据。
 * 对齐 DDD：聚合间通过事件解耦，禁止聚合 A 直接调用聚合 B 的 Service。
 *
 * <p><b>domain-friendly</b>：本接口为纯 JDK（仅依赖 {@link Instant}），不依赖任何框架。
 */
public interface DomainEvent {
    /** 事件发生时间（UTC）。 */
    Instant occurredAt();
    /** 发布该事件的聚合根 id。 */
    String aggregateId();
}

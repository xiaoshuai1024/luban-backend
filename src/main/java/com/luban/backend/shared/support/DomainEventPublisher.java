package com.luban.backend.shared.support;

import com.luban.backend.shared.domain.event.DomainEvent;
import com.luban.backend.shared.mapper.DomainOutboxMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 领域事件双写发布器（at-least-once 投递保障）。
 *
 * <p>替代 Service 直接调用 {@link ApplicationEventPublisher}。调用方（Service）在聚合根
 * pullEvents() 后调用 {@link #publishAll}，本组件在同一事务内完成双写：
 * <ol>
 *   <li>{@code applicationEventPublisher.publishEvent(event)} —— 触发 AFTER_COMMIT handler，
 *       即时 best-effort 投递（现有行为不变）</li>
 *   <li>{@code outboxMapper.insert(...)} —— 事务内落盘，保证事件不丢</li>
 * </ol>
 *
 * <p>正常路径：AFTER_COMMIT handler 即时消费，relay 扫到 processed_at=NULL 的记录重投时
 * 由 handler 幂等去重。失败路径：handler 未消费成功，relay 补偿重发。
 *
     * <p>{@code @Transactional(REQUIRED)} 加入调用方事务（若有），否则新开事务——
     * 保证 outbox insert 与业务写同生共死。
     */
    @Component
    public class DomainEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;
    private final DomainOutboxMapper outboxMapper;

    public DomainEventPublisher(ApplicationEventPublisher applicationEventPublisher,
                                DomainOutboxMapper outboxMapper) {
        this.applicationEventPublisher = applicationEventPublisher;
        this.outboxMapper = outboxMapper;
    }

    /**
     * 双写发布：即时 publishEvent + 事务内 outbox 落盘。
     * 应在调用方的事务上下文内调用（与业务写同事务）；REQUIRED 保证无事务时也能正确提交。
     */
    @Transactional(rollbackFor = Exception.class)
    public void publishAll(List<DomainEvent> events) {
        if (events == null || events.isEmpty()) return;
        for (DomainEvent event : events) {
            applicationEventPublisher.publishEvent(event);
            outboxMapper.insert(DomainEventSerializer.toOutboxRow(event));
        }
    }

    /** 单事件便利方法。 */
    public void publish(DomainEvent event) {
        publishAll(List.of(event));
    }
}

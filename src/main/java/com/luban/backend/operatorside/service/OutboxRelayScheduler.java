package com.luban.backend.operatorside.service;

import com.luban.backend.shared.domain.event.DomainEvent;
import com.luban.backend.shared.entity.DomainOutbox;
import com.luban.backend.shared.mapper.DomainOutboxMapper;
import com.luban.backend.shared.support.DomainEventRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Outbox 补偿投递调度器（at-least-once 事件投递保障）。
 *
 * <p>定时扫描 {@code domain_outbox} 中 {@code processed_at IS NULL} 的记录，反序列化为
 * {@link DomainEvent} 后经 {@link ApplicationEventPublisher} 投递（handler 幂等去重）。
 *
 * <p>语义：
 * <ul>
 *   <li>成功（反序列化 + 投递无异常）→ markProcessed</li>
 *   <li>失败 → incrementAttempts + 指数退避 nextRetryAt（2^attempts 秒，封顶 1h）</li>
 *   <li>超过 {@link #MAX_ATTEMPTS} → 不再重试，ERROR 日志（死信，需人工介入）</li>
 * </ul>
 *
 * <p>正常路径下 AFTER_COMMIT handler 已即时消费，本调度器是<b>补偿通道</b>——
 * 只在 handler 失败/未触发时承担重发。幂等性由 handler 端保证（relay 可能重投）。
 */
@Component
public class OutboxRelayScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayScheduler.class);

    /** 单次扫描批量（控制事务大小与延迟）。 */
    private static final int BATCH_SIZE = 50;
    /** 最大重试次数，超过则判死信。 */
    private static final int MAX_ATTEMPTS = 5;
    /** 退避封顶：1 小时。 */
    private static final Duration MAX_BACKOFF = Duration.ofHours(1);

    private final DomainOutboxMapper outboxMapper;
    private final ApplicationEventPublisher eventPublisher;

    public OutboxRelayScheduler(DomainOutboxMapper outboxMapper,
                                ApplicationEventPublisher eventPublisher) {
        this.outboxMapper = outboxMapper;
        this.eventPublisher = eventPublisher;
    }

    /** 每 5 秒扫描一次待补偿事件。 */
    @Scheduled(fixedDelay = 5000)
    public void relay() {
        List<DomainOutbox> pending = outboxMapper.fetchPending(Instant.now(), BATCH_SIZE);
        if (pending.isEmpty()) return;
        log.debug("outbox relay 扫描到 {} 条待处理事件", pending.size());

        for (DomainOutbox row : pending) {
            // 超过最大重试 → 死信
            if (row.getAttempts() >= MAX_ATTEMPTS) {
                log.error("outbox 事件 {} 达到最大重试次数 {}，判为死信（event_type={}, aggregate_id={}）",
                        row.getId(), MAX_ATTEMPTS, row.getEventType(), row.getAggregateId());
                // 标记处理（避免无限扫描），死信由人工介入
                outboxMapper.markProcessed(row.getId(), Instant.now());
                continue;
            }
            processOne(row);
        }
    }

    private void processOne(DomainOutbox row) {
        try {
            DomainEvent event = DomainEventRegistry.deserialize(row.getEventType(), row.getPayloadJson());
            if (event == null) {
                throw new IllegalStateException("无法反序列化事件: " + row.getEventType());
            }
            // 投递（handler 幂等去重）
            eventPublisher.publishEvent(event);
            outboxMapper.markProcessed(row.getId(), Instant.now());
            log.debug("outbox 事件 {} 已补偿投递", row.getId());
        } catch (Exception e) {
            // 失败：退避后重试
            Instant nextRetry = Instant.now().plus(computeBackoff(row.getAttempts()));
            outboxMapper.incrementAttempts(row.getId(), nextRetry);
            log.warn("outbox 事件 {} 投递失败（第 {} 次），将在 {} 重试: {}",
                    row.getId(), row.getAttempts() + 1, nextRetry, e.getMessage());
        }
    }

    /** 指数退避：2^(attempts+1) 秒，封顶 1h。 */
    private Duration computeBackoff(int attempts) {
        long seconds = (long) Math.pow(2, attempts + 1);
        Duration backoff = Duration.ofSeconds(seconds);
        return backoff.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : backoff;
    }
}

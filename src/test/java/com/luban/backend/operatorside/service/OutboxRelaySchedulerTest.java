package com.luban.backend.operatorside.service;

import com.luban.backend.shared.domain.event.LeadConvertedEvent;
import com.luban.backend.shared.entity.DomainOutbox;
import com.luban.backend.shared.mapper.DomainOutboxMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OutboxRelayScheduler 单测（at-least-once 投递保障）。
 *
 * <p>覆盖：空队列早退、正常投递 markProcessed、反序列化失败 incrementAttempts+退避、
 * 超过 MAX_ATTEMPTS 死信标记。
 */
@ExtendWith(MockitoExtension.class)
class OutboxRelaySchedulerTest {

    @Mock private DomainOutboxMapper outboxMapper;
    @Mock private ApplicationEventPublisher eventPublisher;

    private OutboxRelayScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new OutboxRelayScheduler(outboxMapper, eventPublisher);
    }

    private DomainOutbox row(String id, String eventType, String payload, int attempts) {
        DomainOutbox row = new DomainOutbox();
        row.setId(id);
        row.setAggregateId("agg-1");
        row.setEventType(eventType);
        row.setPayloadJson(payload);
        row.setOccurredAt(Instant.now());
        row.setAttempts(attempts);
        row.setNextRetryAt(Instant.now());
        return row;
    }

    @Test
    void relay_emptyQueue_doesNothing() {
        when(outboxMapper.fetchPending(any(), anyInt())).thenReturn(List.of());

        scheduler.relay();

        verify(outboxMapper, never()).markProcessed(anyString(), any());
        verify(outboxMapper, never()).incrementAttempts(anyString(), any());
    }

    @Test
    void relay_success_publishesEventAndMarksProcessed() {
        String payload = "{\"leadId\":\"l-1\",\"siteId\":\"s-1\",\"convertedAt\":\"2026-01-01T00:00:00Z\",\"occurredAt\":\"2026-01-01T00:00:00Z\"}";
        DomainOutbox r = row("obx-1", "LeadConvertedEvent", payload, 0);
        when(outboxMapper.fetchPending(any(), anyInt())).thenReturn(List.of(r));

        scheduler.relay();

        // 验证投递了 LeadConvertedEvent
        ArgumentCaptor<LeadConvertedEvent> captor = ArgumentCaptor.forClass(LeadConvertedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().leadId()).isEqualTo("l-1");
        // 验证标记处理
        verify(outboxMapper).markProcessed(eq("obx-1"), any());
        verify(outboxMapper, never()).incrementAttempts(anyString(), any());
    }

    @Test
    void relay_deserializeFailure_incrementsAttemptsWithBackoff() {
        DomainOutbox r = row("obx-2", "UnknownEventType", "garbage", 0);
        when(outboxMapper.fetchPending(any(), anyInt())).thenReturn(List.of(r));

        scheduler.relay();

        verify(eventPublisher, never()).publishEvent(any());
        verify(outboxMapper, never()).markProcessed(anyString(), any());
        // 验证退避重试（nextRetryAt 在未来）
        verify(outboxMapper).incrementAttempts(eq("obx-2"), any());
    }

    @Test
    void relay_maxAttemptsReached_marksAsDeadLetter() {
        // attempts=5 = MAX_ATTEMPTS → 死信，标记 processed 不再扫描
        DomainOutbox r = row("obx-3", "LeadConvertedEvent", "valid", 5);
        when(outboxMapper.fetchPending(any(), anyInt())).thenReturn(List.of(r));

        scheduler.relay();

        verify(eventPublisher, never()).publishEvent(any());
        verify(outboxMapper).markProcessed(eq("obx-3"), any());
    }
}

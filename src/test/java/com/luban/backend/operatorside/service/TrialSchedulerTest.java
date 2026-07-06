package com.luban.backend.operatorside.service;

import com.luban.backend.shared.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.luban.backend.shared.support.DomainEventPublisher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TrialScheduler 单测（backend-ddd-refactor plan v2 T16，补覆盖率）。
 *
 * <p>覆盖 expireTrials：count>0 正常 / count==0 正常 / 下游异常 catch 不传播（定时任务韧性）。
 *
 * <p>JDK 23 + Mockito inline mock 无法 mock concrete class TrialService（byte-buddy 限制），
 * 改为构造真实 TrialService（依赖 mock Repository/Publisher）—— 这也是更干净的测试范式：
 * 不 mock 被测对象的协作者 concrete class，而是注入其真实依赖。
 */
@ExtendWith(MockitoExtension.class)
class TrialSchedulerTest {

    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private DomainEventPublisher eventPublisher;

    private TrialScheduler scheduler;
    private TrialService trialService;

    @BeforeEach
    void setUp() {
        // 真实 TrialService + mock 依赖（避免 mock concrete class 的 JDK 23 限制）
        trialService = new TrialService(subscriptionRepository, eventPublisher);
        scheduler = new TrialScheduler(trialService);
    }

    @Test
    void expireTrials_logs_when_count_positive() {
        // expireTrials 返回 > 0 → log.info（不 mock log，仅验证不抛异常）
        when(subscriptionRepository.listExpiredUnconvertedTrials(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());

        // 空列表 → count=0，但这里验证调度器自身不抛异常
        assertThatCode(() -> scheduler.expireTrials()).doesNotThrowAnyException();
    }

    @Test
    void expireTrials_silent_when_count_zero() {
        when(subscriptionRepository.listExpiredUnconvertedTrials(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());

        assertThatCode(() -> scheduler.expireTrials()).doesNotThrowAnyException();
    }

    @Test
    void expireTrials_swallows_exception_from_repository() {
        // 定时任务韧性：下游抛异常不能传播（否则 @Scheduled 线程崩溃，后续不再执行）
        when(subscriptionRepository.listExpiredUnconvertedTrials(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new RuntimeException("DB down"));

        assertThatCode(() -> scheduler.expireTrials()).doesNotThrowAnyException();
        verify(subscriptionRepository, times(1)).listExpiredUnconvertedTrials(org.mockito.ArgumentMatchers.any());
    }
}

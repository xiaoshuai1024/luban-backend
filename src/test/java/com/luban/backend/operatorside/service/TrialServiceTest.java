package com.luban.backend.operatorside.service;

import com.luban.backend.shared.domain.SubscriptionAggregate;
import com.luban.backend.shared.entity.Subscription;
import com.luban.backend.shared.entity.TrialRecord;
import com.luban.backend.shared.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.luban.backend.shared.support.DomainEventPublisher;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TrialService 单测（backend-ddd-refactor plan v2 T16，补覆盖率）。
 *
 * <p>覆盖 expireTrials：空列表返回 0 / 正常降级 / 单个失败容错（catch 后继续循环）；
 * downgrade 三分支：agg!=null（expireTrial + save + 发事件）/ agg==null（markTrialConverted 兜底）。
 */
@ExtendWith(MockitoExtension.class)
class TrialServiceTest {

    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private DomainEventPublisher eventPublisher;

    private TrialService service;

    @BeforeEach
    void setUp() {
        service = new TrialService(subscriptionRepository, eventPublisher);
    }

    private TrialRecord expiredTrial(String userId) {
        TrialRecord t = new TrialRecord();
        t.setUserId(userId);
        t.setTrialPlanCode("pro");
        t.setEndsAt(Instant.now().minusSeconds(60));   // 已到期
        t.setConvertedTo(null);                          // 未转化
        return t;
    }

    private SubscriptionAggregate trialingAggregate(String userId) {
        Subscription s = new Subscription();
        s.setUserId(userId);
        s.setStatus("trialing");
        s.setPlanCode("pro");
        return SubscriptionAggregate.reconstitute(s, expiredTrial(userId));
    }

    @Test
    void expireTrials_returns_zero_when_no_expired_trials() {
        when(subscriptionRepository.listExpiredUnconvertedTrials(any(Instant.class)))
                .thenReturn(List.of());

        int count = service.expireTrials();

        assertThat(count).isZero();
        verify(subscriptionRepository, never()).findByUserId(anyString());
    }

    @Test
    void expireTrials_downgrades_each_expired_trial_and_returns_count() {
        when(subscriptionRepository.listExpiredUnconvertedTrials(any(Instant.class)))
                .thenReturn(List.of(expiredTrial("user-1"), expiredTrial("user-2")));
        when(subscriptionRepository.findByUserId("user-1")).thenReturn(trialingAggregate("user-1"));
        when(subscriptionRepository.findByUserId("user-2")).thenReturn(trialingAggregate("user-2"));

        int count = service.expireTrials();

        assertThat(count).isEqualTo(2);
        verify(subscriptionRepository, times(2)).save(any());
    }

    @Test
    void expireTrials_continues_on_single_downgrade_failure() {
        // user-1 降级抛异常，user-2 正常 → count=1（容错，不中断循环）
        when(subscriptionRepository.listExpiredUnconvertedTrials(any(Instant.class)))
                .thenReturn(List.of(expiredTrial("user-1"), expiredTrial("user-2")));
        when(subscriptionRepository.findByUserId("user-1"))
                .thenThrow(new RuntimeException("DB conflict"));
        when(subscriptionRepository.findByUserId("user-2")).thenReturn(trialingAggregate("user-2"));

        int count = service.expireTrials();

        assertThat(count).isEqualTo(1);   // user-2 成功，user-1 失败被 catch
        verify(subscriptionRepository, times(1)).save(any());
    }

    @Test
    void downgrade_falls_back_to_markTrialConverted_when_aggregate_null() {
        // 订阅记录被并发删除 → agg=null → markTrialConverted 兜底
        when(subscriptionRepository.listExpiredUnconvertedTrials(any(Instant.class)))
                .thenReturn(List.of(expiredTrial("user-1")));
        when(subscriptionRepository.findByUserId("user-1")).thenReturn(null);

        int count = service.expireTrials();

        assertThat(count).isEqualTo(1);
        verify(subscriptionRepository).markTrialConverted(eq("user-1"), eq("free"));
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void downgrade_publishes_events_after_save() {
        when(subscriptionRepository.listExpiredUnconvertedTrials(any(Instant.class)))
                .thenReturn(List.of(expiredTrial("user-1")));
        when(subscriptionRepository.findByUserId("user-1")).thenReturn(trialingAggregate("user-1"));

        service.expireTrials();

        // expireTrial() 产 SubscriptionExpiredEvent → publishAll 至少调用 1 次
        verify(eventPublisher, atLeastOnce())
                .publishAll(org.mockito.ArgumentMatchers.anyList());
    }
}

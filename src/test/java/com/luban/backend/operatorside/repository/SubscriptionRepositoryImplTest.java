package com.luban.backend.operatorside.repository;

import com.luban.backend.shared.domain.SubscriptionAggregate;
import com.luban.backend.shared.entity.Subscription;
import com.luban.backend.shared.entity.TrialRecord;
import com.luban.backend.shared.entity.UsageCounter;
import com.luban.backend.shared.mapper.SubscriptionMapper;
import com.luban.backend.shared.mapper.TrialRecordMapper;
import com.luban.backend.shared.mapper.UsageCounterMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
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
 * SubscriptionRepositoryImpl 单测（backend-ddd-refactor T17）。
 *
 * <p>覆盖 save（subscription insert/update 分支 + trial 持久化策略）、incrementUsageAtomicIfUnderQuota、
 * findByUserId、listExpiredUnconvertedTrials、markTrialConverted。
 */
@ExtendWith(MockitoExtension.class)
class SubscriptionRepositoryImplTest {

    @Mock private SubscriptionMapper subscriptionMapper;
    @Mock private TrialRecordMapper trialMapper;
    @Mock private UsageCounterMapper usageMapper;

    private SubscriptionRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        repository = new SubscriptionRepositoryImpl(subscriptionMapper, trialMapper, usageMapper);
    }

    // ===== findByUserId =====

    @Test
    void findByUserId_returnsNull_whenSubscriptionMissing() {
        when(subscriptionMapper.getByUserId("u-1")).thenReturn(null);

        assertThat(repository.findByUserId("u-1")).isNull();
        verify(trialMapper, never()).getByUserId(anyString());
    }

    @Test
    void findByUserId_reconstitutesAggregateWithTrial() {
        Subscription sub = new Subscription();
        sub.setUserId("u-1");
        sub.setStatus("trialing");
        TrialRecord trial = new TrialRecord();
        trial.setUserId("u-1");
        when(subscriptionMapper.getByUserId("u-1")).thenReturn(sub);
        when(trialMapper.getByUserId("u-1")).thenReturn(trial);

        SubscriptionAggregate agg = repository.findByUserId("u-1");

        assertThat(agg).isNotNull();
        assertThat(agg.toSubscription()).isSameAs(sub);
    }

    // ===== save: subscription insert/update 分支 =====

    @Test
    void save_insertsSubscription_whenUserHasNone() {
        SubscriptionAggregate agg = SubscriptionAggregate.newSubscriptionWithTrial(
                "u-1", "pro", Duration.ofDays(14));
        when(subscriptionMapper.getByUserId("u-1")).thenReturn(null);

        repository.save(agg);

        ArgumentCaptor<Subscription> subCaptor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionMapper).insert(subCaptor.capture());
        verify(subscriptionMapper, never()).update(any());
        assertThat(subCaptor.getValue().getUserId()).isEqualTo("u-1");
        assertThat(subCaptor.getValue().getStatus()).isEqualTo("trialing");
        // trial newly created → insert
        verify(trialMapper).insert(any());
        verify(trialMapper, never()).markConverted(anyString(), anyString());
    }

    @Test
    void save_updatesSubscription_whenUserAlreadyHasOne() {
        // 用 reconstitute 构造非 trialing 态 → isTrialNewlyCreated=false，且 trial=null → 不写 trial
        Subscription existing = new Subscription();
        existing.setUserId("u-1");
        existing.setStatus("active");
        existing.setPlanCode("pro");
        SubscriptionAggregate agg = SubscriptionAggregate.reconstitute(existing, null);
        when(subscriptionMapper.getByUserId("u-1")).thenReturn(existing);

        repository.save(agg);

        verify(subscriptionMapper).update(any());
        verify(subscriptionMapper, never()).insert(any());
        // trial 为 null → 不操作 trialMapper
        verify(trialMapper, never()).insert(any());
        verify(trialMapper, never()).markConverted(anyString(), anyString());
    }

    @Test
    void save_marksTrialConverted_whenTrialConvertedToSet() {
        // 模拟 expireTrial 后：trial.convertedTo 已设 → 走 markConverted
        Subscription sub = new Subscription();
        sub.setUserId("u-1");
        sub.setStatus("trialing");
        sub.setPlanCode("pro");
        SubscriptionAggregate agg = SubscriptionAggregate.newSubscriptionWithTrial(
                "u-1", "pro", Duration.ofDays(14));
        agg.expireTrial();   // trialing → active(free)，设 trial.convertedTo=free
        when(subscriptionMapper.getByUserId("u-1")).thenReturn(sub);

        repository.save(agg);

        verify(subscriptionMapper).update(any());
        // convertedTo != null 且非 newlyCreated → markConverted
        verify(trialMapper).markConverted(eq("u-1"), eq(SubscriptionAggregate.FREE_PLAN));
        verify(trialMapper, never()).insert(any());
    }

    // ===== incrementUsageAtomicIfUnderQuota =====

    @Test
    void incrementUsage_returnsCurrentCountAfterAtomicIncrement() {
        when(usageMapper.getCount(eq("u-1"), anyString(), eq("leads"))).thenReturn(42L);

        long result = repository.incrementUsageAtomicIfUnderQuota("u-1", "leads", 100);

        assertThat(result).isEqualTo(42L);
        ArgumentCaptor<UsageCounter> captor = ArgumentCaptor.forClass(UsageCounter.class);
        verify(usageMapper).incrementAtomicIfUnderQuota(captor.capture(), eq(100));
        assertThat(captor.getValue().getUserId()).isEqualTo("u-1");
        assertThat(captor.getValue().getMetric()).isEqualTo("leads");
    }

    @Test
    void incrementUsage_quotaNonPositive_treatsAsUnlimited() {
        when(usageMapper.getCount(anyString(), anyString(), anyString())).thenReturn(5L);

        long result = repository.incrementUsageAtomicIfUnderQuota("u-1", "leads", 0);

        assertThat(result).isEqualTo(5L);
        // quota<=0 → Integer.MAX_VALUE 作为 effectiveQuota
        verify(usageMapper).incrementAtomicIfUnderQuota(any(), eq(Integer.MAX_VALUE));
    }

    @Test
    void incrementUsage_countNull_returnsZero() {
        when(usageMapper.getCount(anyString(), anyString(), anyString())).thenReturn(null);

        long result = repository.incrementUsageAtomicIfUnderQuota("u-1", "leads", 10);

        assertThat(result).isZero();
    }

    // ===== 其它透传方法 =====

    @Test
    void listExpiredUnconvertedTrials_delegatesToMapper() {
        TrialRecord t = new TrialRecord();
        t.setUserId("u-1");
        Instant now = Instant.now();
        when(trialMapper.listExpiredUnconverted(now)).thenReturn(List.of(t));

        List<TrialRecord> result = repository.listExpiredUnconvertedTrials(now);

        assertThat(result).extracting(TrialRecord::getUserId).containsExactly("u-1");
    }

    @Test
    void markTrialConverted_delegatesToMapper() {
        repository.markTrialConverted("u-1", "pro");
        verify(trialMapper).markConverted("u-1", "pro");
    }

    @Test
    void listUsageByUserPeriod_delegatesToMapper() {
        repository.listUsageByUserPeriod("u-1", "2026-07");
        verify(usageMapper).listByUserPeriod("u-1", "2026-07");
    }
}

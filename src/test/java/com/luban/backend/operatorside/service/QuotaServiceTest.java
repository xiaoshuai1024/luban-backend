package com.luban.backend.operatorside.service;

import com.luban.backend.shared.domain.SubscriptionAggregate;
import com.luban.backend.shared.entity.Plan;
import com.luban.backend.shared.entity.Subscription;
import com.luban.backend.shared.exception.BusinessException;
import com.luban.backend.shared.mapper.PlanMapper;
import com.luban.backend.shared.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuotaServiceTest {

    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private PlanMapper planMapper;

    private QuotaService service;

    @BeforeEach
    void setUp() {
        service = new QuotaService(subscriptionRepository, planMapper);
    }

    private static Plan planOf(String code, int leads, int pages, int visits) {
        Plan p = new Plan();
        p.setPlanCode(code);
        p.setQuotaLeads(leads);
        p.setQuotaPages(pages);
        p.setQuotaVisits(visits);
        return p;
    }

    private SubscriptionAggregate trialingAgg(String userId) {
        Subscription s = new Subscription();
        s.setUserId(userId);
        s.setStatus("trialing");
        s.setPlanCode("growth");
        return SubscriptionAggregate.reconstitute(s, null);
    }

    @Test
    void checkAndIncrement_null_user_skips() {
        service.checkAndIncrement(null, "leads");
        verifyNoInteractions(subscriptionRepository);
        verifyNoInteractions(planMapper);
    }

    @Test
    void checkAndIncrement_blank_user_skips() {
        service.checkAndIncrement("  ", "leads");
        verifyNoInteractions(subscriptionRepository);
        verifyNoInteractions(planMapper);
    }

    @Test
    void checkAndIncrement_zero_quota_unlimited() {
        when(subscriptionRepository.findByUserId("u1")).thenReturn(trialingAgg("u1"));
        when(planMapper.getByCode("growth")).thenReturn(planOf("growth", 0, 0, 0));

        assertThatCode(() -> service.checkAndIncrement("u1", "leads")).doesNotThrowAnyException();

        verify(subscriptionRepository).incrementUsageAtomicIfUnderQuota(eq("u1"), eq("leads"), eq(Integer.MAX_VALUE));
    }

    @Test
    void checkAndIncrement_under_quota_passes() {
        when(subscriptionRepository.findByUserId("u1")).thenReturn(trialingAgg("u1"));
        when(planMapper.getByCode("growth")).thenReturn(planOf("growth", 100, 50, 1000));
        when(subscriptionRepository.incrementUsageAtomicIfUnderQuota("u1", "leads", 100)).thenReturn(5L);

        assertThatCode(() -> service.checkAndIncrement("u1", "leads")).doesNotThrowAnyException();
        verify(subscriptionRepository).incrementUsageAtomicIfUnderQuota("u1", "leads", 100);
    }

    @Test
    void checkAndIncrement_over_quota_throws_via_aggregate() {
        when(subscriptionRepository.findByUserId("u1")).thenReturn(trialingAgg("u1"));
        when(planMapper.getByCode("growth")).thenReturn(planOf("growth", 100, 50, 1000));
        when(subscriptionRepository.incrementUsageAtomicIfUnderQuota("u1", "leads", 100)).thenReturn(100L);

        assertThatThrownBy(() -> service.checkAndIncrement("u1", "leads"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("QUOTA_EXCEEDED");
    }

    @Test
    void checkAndIncrement_over_quota_throws_via_inline_when_no_aggregate() {
        when(subscriptionRepository.findByUserId("u1")).thenReturn(null);
        Plan free = planOf("free", 5, 1, 50);
        when(planMapper.getByCode("free")).thenReturn(free);
        when(subscriptionRepository.incrementUsageAtomicIfUnderQuota("u1", "leads", 5)).thenReturn(5L);

        assertThatThrownBy(() -> service.checkAndIncrement("u1", "leads"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("QUOTA_EXCEEDED");
    }

    @Test
    void checkAndIncrement_unknown_metric_treated_as_unlimited() {
        when(subscriptionRepository.findByUserId("u1")).thenReturn(trialingAgg("u1"));
        when(planMapper.getByCode("growth")).thenReturn(planOf("growth", 100, 50, 1000));

        assertThatCode(() -> service.checkAndIncrement("u1", "weird_metric")).doesNotThrowAnyException();
        verify(subscriptionRepository).incrementUsageAtomicIfUnderQuota(eq("u1"), eq("weird_metric"), eq(Integer.MAX_VALUE));
    }
}

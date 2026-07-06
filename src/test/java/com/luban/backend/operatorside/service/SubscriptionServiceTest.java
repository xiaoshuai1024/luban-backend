package com.luban.backend.operatorside.service;

import com.luban.backend.shared.domain.SubscriptionAggregate;
import com.luban.backend.shared.domain.event.SubscriptionUpgradedEvent;
import com.luban.backend.shared.dto.SubscribeRequest;
import com.luban.backend.shared.dto.SubscriptionResponse;
import com.luban.backend.shared.entity.Plan;
import com.luban.backend.shared.entity.Subscription;
import com.luban.backend.shared.entity.UsageCounter;
import com.luban.backend.shared.exception.BusinessException;
import com.luban.backend.shared.repository.PlanRepository;
import com.luban.backend.shared.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.luban.backend.shared.support.DomainEventPublisher;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private PlanRepository planRepository;
    @Mock private PlanService planService;
    @Mock private DomainEventPublisher eventPublisher;

    private SubscriptionService service;

    @BeforeEach
    void setUp() {
        service = new SubscriptionService(subscriptionRepository, planRepository, planService, eventPublisher);
    }

    private static Plan planOf(String code, String name, int leads, int pages, int visits, String gates) {
        Plan p = new Plan();
        p.setPlanCode(code);
        p.setName(name);
        p.setQuotaLeads(leads);
        p.setQuotaPages(pages);
        p.setQuotaVisits(visits);
        p.setGates(gates);
        return p;
    }

    @Test
    void initForNewUser_creates_trial_subscription_when_absent() {
        when(subscriptionRepository.findByUserId("u1")).thenReturn(null);

        service.initForNewUser("u1");

        ArgumentCaptor<SubscriptionAggregate> captor = ArgumentCaptor.forClass(SubscriptionAggregate.class);
        verify(subscriptionRepository).save(captor.capture());
        Subscription sub = captor.getValue().toSubscription();
        assertThat(sub.getUserId()).isEqualTo("u1");
        assertThat(sub.getStatus()).isEqualTo("trialing");
        assertThat(sub.getPlanCode()).isEqualTo("starter");
        assertThat(sub.getTrialStartedAt()).isNotNull();
        Duration d = Duration.between(sub.getTrialStartedAt(), sub.getTrialEndsAt());
        assertThat(d.toDays()).isEqualTo(14);
    }

    @Test
    void initForNewUser_idempotent_when_subscription_exists() {
        Subscription existing = new Subscription();
        existing.setUserId("u1");
        existing.setStatus("trialing");
        when(subscriptionRepository.findByUserId("u1"))
                .thenReturn(SubscriptionAggregate.reconstitute(existing, null));

        service.initForNewUser("u1");

        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void subscribe_transitions_to_active_and_publishes_event() {
        Plan target = planOf("growth", "增长版", 100, 50, 10000, "[\"analytics\"]");
        when(planRepository.getByCode("growth")).thenReturn(Optional.of(target));
        Subscription existing = new Subscription();
        existing.setUserId("u1");
        existing.setStatus("trialing");
        existing.setPlanCode("starter");
        existing.setTrialStartedAt(Instant.now());
        existing.setTrialEndsAt(Instant.now().plus(Duration.ofDays(10)));
        when(subscriptionRepository.findByUserId("u1"))
                .thenReturn(SubscriptionAggregate.reconstitute(existing, null));
        doNothing().when(subscriptionRepository).save(any());

        SubscriptionResponse resp = service.subscribe("u1", new SubscribeRequest("growth"));

        assertThat(resp.planCode()).isEqualTo("growth");
        assertThat(resp.status()).isEqualTo("active");
        assertThat(resp.trialEndsAt()).isNull();
        verify(eventPublisher).publishAll(org.mockito.ArgumentMatchers.anyList());
        verify(subscriptionRepository).save(any());
    }

    @Test
    void subscribe_invalid_plan_throws() {
        when(planRepository.getByCode("nonexistent")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.subscribe("u1", new SubscribeRequest("nonexistent")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("INVALID_PLAN");
        verify(subscriptionRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void subscribe_subscription_not_found_throws() {
        when(planRepository.getByCode("growth")).thenReturn(Optional.of(planOf("growth", "G", 1, 1, 1, "[]")));
        when(subscriptionRepository.findByUserId("u1")).thenReturn(null);

        assertThatThrownBy(() -> service.subscribe("u1", new SubscribeRequest("growth")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("SUBSCRIPTION_NOT_FOUND");
    }

    @Test
    void subscribe_illegal_state_transition_throws() {
        when(planRepository.getByCode("growth")).thenReturn(Optional.of(planOf("growth", "G", 1, 1, 1, "[]")));
        Subscription existing = new Subscription();
        existing.setUserId("u1");
        existing.setStatus("active");
        when(subscriptionRepository.findByUserId("u1"))
                .thenReturn(SubscriptionAggregate.reconstitute(existing, null));

        assertThatThrownBy(() -> service.subscribe("u1", new SubscribeRequest("growth")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("INVALID_STATE_TRANSITION");
    }

    @Test
    void getMyPlan_default_free_when_no_subscription() {
        when(subscriptionRepository.findByUserId("u1")).thenReturn(null);
        Plan free = planOf("free", "免费版", 10, 3, 100, "[\"lead_capture\"]");
        when(planRepository.getByCode("free")).thenReturn(Optional.of(free));
        when(planService.parseGates("[\"lead_capture\"]")).thenReturn(List.of("lead_capture"));
        when(subscriptionRepository.listUsageByUserPeriod(eq("u1"), anyString())).thenReturn(List.of());

        SubscriptionService.MyPlanInfo info = service.getMyPlan("u1");

        assertThat(info.subscription().planCode()).isEqualTo("free");
        assertThat(info.subscription().status()).isEqualTo("active");
        assertThat(info.planName()).isEqualTo("免费版");
        assertThat(info.gates()).containsExactly("lead_capture");
    }

    @Test
    void getUsage_aggregates_counters_by_metric() {
        Subscription existing = new Subscription();
        existing.setUserId("u1");
        existing.setStatus("active");
        existing.setPlanCode("starter");
        when(subscriptionRepository.findByUserId("u1"))
                .thenReturn(SubscriptionAggregate.reconstitute(existing, null));
        Plan starter = planOf("starter", "入门版", 100, 50, 1000, "[]");
        when(planRepository.getByCode("starter")).thenReturn(Optional.of(starter));
        UsageCounter leads = new UsageCounter();
        leads.setMetric("leads"); leads.setCount(42);
        UsageCounter visits = new UsageCounter();
        visits.setMetric("visits"); visits.setCount(99);
        when(subscriptionRepository.listUsageByUserPeriod("u1", "2026-07")).thenReturn(List.of(leads, visits));

        var usage = service.getUsage("u1", "2026-07");

        assertThat(usage.leads()).isEqualTo(42);
        assertThat(usage.visits()).isEqualTo(99);
        assertThat(usage.pages()).isEqualTo(0);
        assertThat(usage.period()).isEqualTo("2026-07");
        assertThat(usage.quotaLeads()).isEqualTo(100);
    }

    @Test
    void getUsage_uses_free_plan_when_no_subscription() {
        when(subscriptionRepository.findByUserId("u1")).thenReturn(null);
        Plan free = planOf("free", "免费版", 5, 1, 50, "[]");
        when(planRepository.getByCode("free")).thenReturn(Optional.of(free));
        when(subscriptionRepository.listUsageByUserPeriod("u1", "2026-07")).thenReturn(List.of());

        var usage = service.getUsage("u1", "2026-07");

        assertThat(usage.quotaLeads()).isEqualTo(5);
        assertThat(usage.quotaPages()).isEqualTo(1);
    }
}

package com.luban.backend.shared.domain;

import com.luban.backend.shared.domain.event.SubscriptionExpiredEvent;
import com.luban.backend.shared.domain.event.SubscriptionUpgradedEvent;
import com.luban.backend.shared.entity.Subscription;
import com.luban.backend.shared.entity.TrialRecord;
import com.luban.backend.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SubscriptionAggregate 单测（backend-ddd-refactor plan v2 T14）。
 *
 * <p>锁定真聚合根范式不变量（合并 Trial/Quota/Usage，用户确认完整实现）：
 * <ul>
 *   <li>状态机 trialing→active→expired，非法转换抛 invalidStateTransition</li>
 *   <li>工厂 newSubscriptionWithTrial：绑 starter+trialing+14天试用+建 TrialRecord</li>
 *   <li>subscribe：trialing→active，<b>清 trial 字段</b>（修复现状不一致），发 SubscriptionUpgradedEvent</li>
 *   <li>expireTrial：trialing→active(free)，标记 TrialRecord convertedTo=free，发 SubscriptionExpiredEvent</li>
 *   <li>expire：active→expired 显式转换</li>
 *   <li>assertQuotaAvailable：quota=0 无限制；currentCount >= quota 抛 QUOTA_EXCEEDED</li>
 * </ul>
 */
class SubscriptionAggregateTest {

    @Test
    void newSubscriptionWithTrialCreatesTrialingWithStarterPlan() {
        Instant before = Instant.now();
        SubscriptionAggregate agg = SubscriptionAggregate.newSubscriptionWithTrial(
                "u-1", "starter", Duration.ofDays(14));

        Subscription s = agg.toSubscription();
        assertThat(s.getUserId()).isEqualTo("u-1");
        assertThat(s.getPlanCode()).isEqualTo("starter");
        assertThat(s.getStatus()).isEqualTo("trialing");
        assertThat(s.getStartedAt()).isBetween(before, Instant.now().plusSeconds(1));
        assertThat(s.getTrialStartedAt()).isEqualTo(s.getStartedAt());
        // trialEndsAt ≈ startedAt + 14天（用 isAfter/isBefore 容差校验，避免 AssertJ TemporalOffset 复杂性）
        assertThat(s.getTrialEndsAt()).isAfter(s.getStartedAt().plus(Duration.ofDays(14).minusSeconds(5)));
        assertThat(s.getTrialEndsAt()).isBefore(s.getStartedAt().plus(Duration.ofDays(14).plusSeconds(5)));

        TrialRecord trial = agg.toTrialRecord();
        assertThat(trial.getUserId()).isEqualTo("u-1");
        assertThat(trial.getTrialPlanCode()).isEqualTo("starter");
        assertThat(trial.getConvertedTo()).isNull();   // 未降级
    }

    @Test
    void subscribeTransitionsTrialingToActiveAndClearsTrialFields() {
        // 关键修复：subscribe 须清 trial 字段（现状不清留不一致）
        SubscriptionAggregate agg = SubscriptionAggregate.newSubscriptionWithTrial(
                "u-1", "starter", Duration.ofDays(14));
        assertThat(agg.toSubscription().getStatus()).isEqualTo("trialing");

        agg.subscribe("growth");

        Subscription s = agg.toSubscription();
        assertThat(s.getStatus()).isEqualTo("active");
        assertThat(s.getPlanCode()).isEqualTo("growth");
        // 清理 trial 字段（修复现状不一致）：trialStartedAt/trialEndsAt 置 null
        assertThat(s.getTrialStartedAt()).isNull();
        assertThat(s.getTrialEndsAt()).isNull();

        // 发 SubscriptionUpgradedEvent
        var events = agg.pullEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(SubscriptionUpgradedEvent.class);
        SubscriptionUpgradedEvent ev = (SubscriptionUpgradedEvent) events.get(0);
        assertThat(ev.fromPlan()).isEqualTo("starter");
        assertThat(ev.toPlan()).isEqualTo("growth");
    }

    @Test
    void expireTrialDowngradesTrialingToFreeActive() {
        SubscriptionAggregate agg = SubscriptionAggregate.newSubscriptionWithTrial(
                "u-1", "starter", Duration.ofDays(14));

        agg.expireTrial();

        Subscription s = agg.toSubscription();
        assertThat(s.getStatus()).isEqualTo("active");
        assertThat(s.getPlanCode()).isEqualTo("free");    // 降级到 free
        assertThat(s.getTrialEndsAt()).isNotNull();        // 记录降级时间

        TrialRecord trial = agg.toTrialRecord();
        assertThat(trial.getConvertedTo()).isEqualTo("free");   // 标记已降级

        var events = agg.pullEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(SubscriptionExpiredEvent.class);
        SubscriptionExpiredEvent ev = (SubscriptionExpiredEvent) events.get(0);
        assertThat(ev.fromPlan()).isEqualTo("starter");
        assertThat(ev.toPlan()).isEqualTo("free");
    }

    @Test
    void expireTransitionsActiveToExpired() {
        // active→expired 显式转换（完整状态机）
        Subscription persisted = trialingSubscription();
        persisted.setStatus("active");
        SubscriptionAggregate agg = SubscriptionAggregate.reconstitute(persisted, null);

        agg.expire();

        assertThat(agg.toSubscription().getStatus()).isEqualTo("expired");
    }

    @Test
    void subscribeRejectsAlreadyActive() {
        Subscription persisted = trialingSubscription();
        persisted.setStatus("active");
        SubscriptionAggregate agg = SubscriptionAggregate.reconstitute(persisted, null);

        assertThatThrownBy(() -> agg.subscribe("growth"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo("INVALID_STATE_TRANSITION");
    }

    @Test
    void expireTrialRejectsNonTrialing() {
        Subscription persisted = trialingSubscription();
        persisted.setStatus("active");
        SubscriptionAggregate agg = SubscriptionAggregate.reconstitute(persisted, null);

        assertThatThrownBy(() -> agg.expireTrial())
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo("INVALID_STATE_TRANSITION");
    }

    @Test
    void assertQuotaAvailablePassesWhenUnlimited() {
        // quota=0 表示无限制
        SubscriptionAggregate agg = SubscriptionAggregate.newSubscriptionWithTrial(
                "u-1", "starter", Duration.ofDays(14));

        agg.assertQuotaAvailable(0, 99999);   // 不抛异常
    }

    @Test
    void assertQuotaAvailablePassesWhenUnderLimit() {
        SubscriptionAggregate agg = SubscriptionAggregate.newSubscriptionWithTrial(
                "u-1", "starter", Duration.ofDays(14));

        agg.assertQuotaAvailable(100, 99);    // 99 < 100，未超限
    }

    @Test
    void assertQuotaAvailableThrowsWhenExceeded() {
        SubscriptionAggregate agg = SubscriptionAggregate.newSubscriptionWithTrial(
                "u-1", "starter", Duration.ofDays(14));

        assertThatThrownBy(() -> agg.assertQuotaAvailable(100, 100))   // 100 >= 100
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo("QUOTA_EXCEEDED");
    }

    @Test
    void reconstitutePresistsStateWithoutEvents() {
        Instant created = Instant.parse("2026-01-01T00:00:00Z");
        Subscription persisted = new Subscription();
        persisted.setUserId("u-9");
        persisted.setPlanCode("growth");
        persisted.setStatus("active");
        persisted.setStartedAt(created);
        TrialRecord trial = new TrialRecord();
        trial.setUserId("u-9");
        trial.setTrialPlanCode("starter");
        trial.setConvertedTo("growth");

        SubscriptionAggregate agg = SubscriptionAggregate.reconstitute(persisted, trial);

        assertThat(agg.toSubscription().getPlanCode()).isEqualTo("growth");
        assertThat(agg.toTrialRecord().getConvertedTo()).isEqualTo("growth");
        assertThat(agg.pullEvents()).isEmpty();
    }

    @Test
    void pullEventsDrains() {
        SubscriptionAggregate agg = SubscriptionAggregate.newSubscriptionWithTrial(
                "u-1", "starter", Duration.ofDays(14));
        agg.subscribe("growth");

        assertThat(agg.pullEvents()).hasSize(1);
        assertThat(agg.pullEvents()).isEmpty();
    }

    private static Subscription trialingSubscription() {
        Subscription s = new Subscription();
        s.setUserId("u-1");
        s.setPlanCode("starter");
        s.setStatus("trialing");
        s.setStartedAt(Instant.parse("2026-01-01T00:00:00Z"));
        s.setTrialStartedAt(Instant.parse("2026-01-01T00:00:00Z"));
        s.setTrialEndsAt(Instant.parse("2026-01-15T00:00:00Z"));
        return s;
    }
}

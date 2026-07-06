package com.luban.backend.shared.domain;

import com.luban.backend.shared.domain.event.DomainEvent;
import com.luban.backend.shared.domain.event.SubscriptionExpiredEvent;
import com.luban.backend.shared.domain.event.SubscriptionUpgradedEvent;
import com.luban.backend.shared.entity.Subscription;
import com.luban.backend.shared.entity.TrialRecord;
import com.luban.backend.shared.exception.BusinessException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 订阅聚合根（backend-ddd-refactor plan v2 T14）。
 *
 * <p>封装 Subscription 域不变量（合并 Trial/Quota/Usage，用户确认完整实现，
 * 对齐 {@code .agents/rules/luban-engineering-principles.md}）：
 *
 * <p><b>状态机</b>（显式转换，非法转换抛 invalidStateTransition）：
 * <pre>
 *   (new) ──newSubscriptionWithTrial──▶ trialing
 *   trialing ──subscribe(plan)──▶ active        （清 trial 字段，发 SubscriptionUpgradedEvent）
 *   trialing ──expireTrial()──▶ active(free)    （标记 TrialRecord.convertedTo=free，发 SubscriptionExpiredEvent）
 *   active ──expire()──▶ expired                 （显式转换，完整状态机）
 * </pre>
 *
 * <p><b>修复现状不一致</b>（用户确认完整实现）：
 * <ul>
 *   <li>{@code subscribe()} 清 trialStartedAt/trialEndsAt（现状不清，TrialScheduler 可能误扫）</li>
 *   <li>Quota 决策在聚合根 {@link #assertQuotaAvailable}（原子 check-and-increment 在 Repository）</li>
 * </ul>
 *
 * @see Subscription
 * @see TrialRecord
 */
public final class SubscriptionAggregate {

    public static final String STATUS_TRIALING = "trialing";
    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_EXPIRED = "expired";
    public static final String FREE_PLAN = "free";

    private final Subscription root;
    private final TrialRecord trial;        // 聚合内实体（可能为 null——非试用态或无试用记录）
    private final List<DomainEvent> events = new ArrayList<>();

    private SubscriptionAggregate(Subscription root, TrialRecord trial) {
        this.root = root;
        this.trial = trial;
    }

    /**
     * 工厂：创建带试用的新订阅（trialing + 指定 trialPlan + trialDuration）。
     */
    public static SubscriptionAggregate newSubscriptionWithTrial(String userId, String trialPlanCode,
                                                                 Duration trialDuration) {
        Instant now = Instant.now();
        Instant trialEnds = now.plus(trialDuration);

        Subscription s = new Subscription();
        s.setUserId(userId);
        s.setPlanCode(trialPlanCode);          // 试用期内享 trialPlan 配额
        s.setStatus(STATUS_TRIALING);
        s.setStartedAt(now);
        s.setTrialStartedAt(now);
        s.setTrialEndsAt(trialEnds);

        TrialRecord trial = new TrialRecord();
        trial.setUserId(userId);
        trial.setTrialPlanCode(trialPlanCode);
        trial.setStartedAt(now);
        trial.setEndsAt(trialEnds);
        // convertedTo = null（未降级）

        return new SubscriptionAggregate(s, trial);
    }

    /** 工厂：从持久化重建（保留原始状态，不发事件）。trial 可为 null。 */
    public static SubscriptionAggregate reconstitute(Subscription persisted, TrialRecord trial) {
        return new SubscriptionAggregate(persisted, trial);
    }

    /**
     * 切换套餐（trialing→active）。
     *
     * <p><b>修复现状不一致</b>：清 trialStartedAt/trialEndsAt，避免 TrialScheduler 误扫
     * （现状不清这些字段，subscribe 后 trial 字段 linger，scheduler 仍可能处理）。
     *
     * @param newPlanCode 新套餐 code
     */
    public void subscribe(String newPlanCode) {
        assertTransition(STATUS_TRIALING, STATUS_ACTIVE);
        String fromPlan = root.getPlanCode();
        root.setPlanCode(newPlanCode);
        root.setStatus(STATUS_ACTIVE);
        // 清 trial 字段（修复不一致）
        root.setTrialStartedAt(null);
        root.setTrialEndsAt(null);
        // 标记 TrialRecord 已转化（若存在）
        if (trial != null) {
            trial.setConvertedTo(newPlanCode);
        }
        events.add(new SubscriptionUpgradedEvent(root.getUserId(), fromPlan, newPlanCode, Instant.now()));
    }

    /**
     * 试用到期降级（trialing→active(free)）。
     * 由 TrialScheduler 扫描到期试用后调用。标记 TrialRecord.convertedTo=free。
     */
    public void expireTrial() {
        assertTransition(STATUS_TRIALING, STATUS_ACTIVE);
        String fromPlan = root.getPlanCode();
        Instant now = Instant.now();
        root.setPlanCode(FREE_PLAN);
        root.setStatus(STATUS_ACTIVE);
        root.setTrialEndsAt(now);    // 记录降级时间
        if (trial != null) {
            trial.setConvertedTo(FREE_PLAN);
        }
        events.add(new SubscriptionExpiredEvent(root.getUserId(), fromPlan, FREE_PLAN, now));
    }

    /** 显式过期（active→expired）。完整状态机的显式转换，发 SubscriptionExpiredEvent（与 expireTrial 一致，供 FeatureGate 降级消费）。 */
    public void expire() {
        assertTransition(STATUS_ACTIVE, STATUS_EXPIRED);
        String fromPlan = root.getPlanCode();
        Instant now = Instant.now();
        root.setStatus(STATUS_EXPIRED);
        events.add(new SubscriptionExpiredEvent(root.getUserId(), fromPlan, null, now));
    }

    /**
     * 断言配额可用（聚合根决策，原子 check-and-increment 在 Repository）。
     *
     * @param quota        套餐配额上限（0 表示无限制）
     * @param currentCount 当前周期已用量（Service/Repository 查询后传入）
     * @throws BusinessException quota>0 且 currentCount >= quota 时 QUOTA_EXCEEDED(429)
     */
    public void assertQuotaAvailable(int quota, long currentCount) {
        if (quota <= 0) {
            return;   // 无限制
        }
        if (currentCount >= quota) {
            throw BusinessException.quotaExceeded(
                    String.format("用量超限：已用 %d/%d，请升级套餐", currentCount, quota));
        }
    }

    public Subscription toSubscription() {
        return root;
    }

    /** 导出 TrialRecord（Repository.save 持久化用）。可能为 null（非试用态）。 */
    public TrialRecord toTrialRecord() {
        return trial;
    }

    /** 是否为新建试用记录（Repository 判断 insert vs update TrialRecord）。 */
    public boolean isTrialNewlyCreated() {
        return trial != null && trial.getConvertedTo() == null
                && STATUS_TRIALING.equals(root.getStatus());
    }

    public List<DomainEvent> pullEvents() {
        List<DomainEvent> drained = new ArrayList<>(events);
        events.clear();
        return drained;
    }

    private void assertTransition(String expectedFrom, String to) {
        if (!expectedFrom.equals(root.getStatus())) {
            throw BusinessException.invalidStateTransition(root.getStatus(), to);
        }
    }
}

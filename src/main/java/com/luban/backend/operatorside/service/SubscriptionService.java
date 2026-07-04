package com.luban.backend.operatorside.service;

import com.luban.backend.shared.domain.SubscriptionAggregate;
import com.luban.backend.shared.dto.PlanResponse;
import com.luban.backend.shared.dto.SubscribeRequest;
import com.luban.backend.shared.dto.SubscriptionResponse;
import com.luban.backend.shared.dto.UsageResponse;
import com.luban.backend.shared.entity.Plan;
import com.luban.backend.shared.entity.Subscription;
import com.luban.backend.shared.exception.BusinessException;
import com.luban.backend.shared.mapper.PlanMapper;
import com.luban.backend.shared.repository.SubscriptionRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 订阅应用服务（backend-ddd-refactor plan v2 T14）。
 *
 * <p>用例编排：加载聚合根 → 调聚合根方法 → 保存 → 发布事件。
 * 状态机 + 试用管理下沉到 {@link SubscriptionAggregate}。
 *
 * <p>修复现状不一致（用户确认完整实现）：subscribe 经聚合根，清 trial 字段，
 * 并发布 SubscriptionUpgradedEvent（FeatureGate 等经事件解耦）。
 */
@Service
public class SubscriptionService {

    private static final String FREE_PLAN = "free";
    private static final String STARTER_PLAN = "starter";
    private static final Duration TRIAL_DURATION = Duration.ofDays(14);

    private final SubscriptionRepository subscriptionRepository;
    private final PlanMapper planMapper;
    private final PlanService planService;
    private final ApplicationEventPublisher eventPublisher;

    public SubscriptionService(SubscriptionRepository subscriptionRepository, PlanMapper planMapper,
                               PlanService planService,
                               ApplicationEventPublisher eventPublisher) {
        this.subscriptionRepository = subscriptionRepository;
        this.planMapper = planMapper;
        this.planService = planService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 注册时初始化订阅：绑 starter plan + 触发 14 天试用（trialing）。
     * 幂等：若已有订阅则跳过。
     */
    @Transactional(rollbackFor = Exception.class)
    public void initForNewUser(String userId) {
        if (subscriptionRepository.findByUserId(userId) != null) return;

        SubscriptionAggregate agg = SubscriptionAggregate.newSubscriptionWithTrial(
                userId, STARTER_PLAN, TRIAL_DURATION);
        subscriptionRepository.save(agg);
    }

    /** 切换套餐（trialing→active，聚合根清 trial 字段并发 SubscriptionUpgradedEvent）。 */
    @Transactional(rollbackFor = Exception.class)
    public SubscriptionResponse subscribe(String userId, SubscribeRequest req) {
        Plan target = planMapper.getByCode(req.planCode());
        if (target == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_PLAN", "无效的套餐: " + req.planCode());
        }
        SubscriptionAggregate agg = subscriptionRepository.findByUserId(userId);
        if (agg == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "SUBSCRIPTION_NOT_FOUND", "订阅记录不存在");
        }
        agg.subscribe(req.planCode());   // 聚合根状态机 + 清 trial 字段
        subscriptionRepository.save(agg);
        publishEvents(agg);
        return SubscriptionResponse.fromEntity(agg.toSubscription());
    }

    /** 当前订阅 + 用量聚合（GET /billing/me）。 */
    public MyPlanInfo getMyPlan(String userId) {
        SubscriptionAggregate agg = subscriptionRepository.findByUserId(userId);
        if (agg == null) {
            return defaultFree(userId);
        }
        Subscription sub = agg.toSubscription();
        Plan plan = planMapper.getByCode(sub.getPlanCode());
        if (plan == null) plan = planMapper.getByCode(FREE_PLAN);

        UsageResponse usage = buildUsage(userId, plan);
        SubscriptionResponse subResp = SubscriptionResponse.fromEntity(sub);
        List<String> gates = planService.parseGates(plan.getGates());
        return new MyPlanInfo(subResp, plan.getName(), gates, usage);
    }

    /** GET /billing/usage。 */
    public UsageResponse getUsage(String userId, String periodMonth) {
        SubscriptionAggregate agg = subscriptionRepository.findByUserId(userId);
        Plan plan = agg != null ? planMapper.getByCode(agg.toSubscription().getPlanCode())
                                : planMapper.getByCode(FREE_PLAN);
        if (plan == null) plan = planMapper.getByCode(FREE_PLAN);
        return buildUsage(userId == null ? "" : userId, periodMonth, plan);
    }

    private UsageResponse buildUsage(String userId, Plan plan) {
        String period = LocalDate.now(ZoneOffset.UTC).toString().substring(0, 7);
        return buildUsage(userId, period, plan);
    }

    private UsageResponse buildUsage(String userId, String periodMonth, Plan plan) {
        long leads = 0, pages = 0, visits = 0;
        if (userId != null && !userId.isBlank()) {
            for (var c : subscriptionRepository.listUsageByUserPeriod(userId, periodMonth)) {
                switch (c.getMetric()) {
                    case "leads" -> leads = c.getCount();
                    case "pages" -> pages = c.getCount();
                    case "visits" -> visits = c.getCount();
                }
            }
        }
        return new UsageResponse(leads, pages, visits, periodMonth,
                plan.getQuotaLeads(), plan.getQuotaPages(), plan.getQuotaVisits());
    }

    private MyPlanInfo defaultFree(String userId) {
        Plan free = planMapper.getByCode(FREE_PLAN);
        return new MyPlanInfo(
                new SubscriptionResponse(FREE_PLAN, "active", Instant.now(), null, null),
                free != null ? free.getName() : "免费版",
                free != null ? planService.parseGates(free.getGates()) : List.of("lead_capture"),
                buildUsage(userId, free != null ? free : planMapper.getByCode(FREE_PLAN))
        );
    }

    /** /billing/me 聚合响应（订阅 + 套餐名 + gates + 用量）。 */
    public record MyPlanInfo(
            SubscriptionResponse subscription,
            String planName,
            List<String> gates,
            UsageResponse usage
    ) {}

    /** 套餐列表（透传 PlanService）。 */
    public List<PlanResponse> listPlans() {
        return planService.listPlans();
    }

    private void publishEvents(SubscriptionAggregate agg) {
        agg.pullEvents().forEach(eventPublisher::publishEvent);
    }
}

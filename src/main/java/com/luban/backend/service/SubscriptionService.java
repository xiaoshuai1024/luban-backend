package com.luban.backend.service;

import com.luban.backend.dto.PlanResponse;
import com.luban.backend.dto.SubscribeRequest;
import com.luban.backend.dto.SubscriptionResponse;
import com.luban.backend.dto.UsageResponse;
import com.luban.backend.entity.Plan;
import com.luban.backend.entity.Subscription;
import com.luban.backend.entity.TrialRecord;
import com.luban.backend.exception.BusinessException;
import com.luban.backend.mapper.PlanMapper;
import com.luban.backend.mapper.SubscriptionMapper;
import com.luban.backend.mapper.TrialRecordMapper;
import com.luban.backend.mapper.UsageCounterMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 订阅服务（v02 billing 域）。
 *
 * 核心职责：
 * - 注册初始化：新用户绑 free plan + 建 starter 试用（14 天 trialing）
 * - 切换套餐（价格全 0，仅更新 plan_code）
 * - 读取当前订阅 + 用量（/billing/me 聚合）
 */
@Service
public class SubscriptionService {

    private static final String FREE_PLAN = "free";
    private static final String STARTER_PLAN = "starter";
    private static final int TRIAL_DAYS = 14;

    private final SubscriptionMapper subscriptionMapper;
    private final PlanMapper planMapper;
    private final TrialRecordMapper trialRecordMapper;
    private final UsageCounterMapper usageCounterMapper;
    private final PlanService planService;

    public SubscriptionService(SubscriptionMapper subscriptionMapper, PlanMapper planMapper,
                               TrialRecordMapper trialRecordMapper, UsageCounterMapper usageCounterMapper,
                               PlanService planService) {
        this.subscriptionMapper = subscriptionMapper;
        this.planMapper = planMapper;
        this.trialRecordMapper = trialRecordMapper;
        this.usageCounterMapper = usageCounterMapper;
        this.planService = planService;
    }

    /**
     * 注册时初始化订阅：绑 free + 触发 14 天 starter 试用。
     * 幂等：若已有订阅则跳过（不重复建试用）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void initForNewUser(String userId) {
        if (subscriptionMapper.getByUserId(userId) != null) return;

        Instant now = Instant.now();
        Instant trialEnds = now.plus(java.time.Duration.ofDays(TRIAL_DAYS));

        // 绑 free plan，状态 trialing（试用期内享受 starter 配额）
        Subscription sub = new Subscription();
        sub.setUserId(userId);
        sub.setPlanCode(STARTER_PLAN);      // 试用期内 plan = starter（享 starter 配额）
        sub.setStatus("trialing");
        sub.setStartedAt(now);
        sub.setTrialStartedAt(now);
        sub.setTrialEndsAt(trialEnds);
        subscriptionMapper.insert(sub);

        // 建试用记录（供 TrialScheduler 扫描降级）
        TrialRecord trial = new TrialRecord();
        trial.setUserId(userId);
        trial.setTrialPlanCode(STARTER_PLAN);
        trial.setStartedAt(now);
        trial.setEndsAt(trialEnds);
        trialRecordMapper.insert(trial);
    }

    /** 切换套餐（价格全 0，无扣费）。仅 active/trialing 状态可切。 */
    @Transactional(rollbackFor = Exception.class)
    public SubscriptionResponse subscribe(String userId, SubscribeRequest req) {
        Plan target = planMapper.getByCode(req.planCode());
        if (target == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_PLAN", "无效的套餐: " + req.planCode());
        }
        Subscription sub = subscriptionMapper.getByUserId(userId);
        if (sub == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "SUBSCRIPTION_NOT_FOUND", "订阅记录不存在");
        }
        sub.setPlanCode(req.planCode());
        sub.setStatus("active");    // 切换后变为 active（结束试用态）
        subscriptionMapper.update(sub);
        return SubscriptionResponse.fromEntity(sub);
    }

    /** 当前订阅 + 用量聚合（GET /billing/me）。 */
    public MyPlanInfo getMyPlan(String userId) {
        Subscription sub = subscriptionMapper.getByUserId(userId);
        if (sub == null) {
            // 无订阅记录：兜底返回 free（兼容历史用户）
            return defaultFree(userId);
        }
        Plan plan = planMapper.getByCode(sub.getPlanCode());
        if (plan == null) plan = planMapper.getByCode(FREE_PLAN);

        UsageResponse usage = buildUsage(userId, plan);
        SubscriptionResponse subResp = SubscriptionResponse.fromEntity(sub);
        List<String> gates = planService.parseGates(plan.getGates());
        return new MyPlanInfo(subResp, plan.getName(), gates, usage);
    }

    /** GET /billing/usage。 */
    public UsageResponse getUsage(String userId, String periodMonth) {
        Subscription sub = subscriptionMapper.getByUserId(userId);
        Plan plan = sub != null ? planMapper.getByCode(sub.getPlanCode()) : planMapper.getByCode(FREE_PLAN);
        if (plan == null) plan = planMapper.getByCode(FREE_PLAN);
        return buildUsage(userId == null ? "" : userId, periodMonth, plan);
    }

    private UsageResponse buildUsage(String userId, Plan plan) {
        String period = LocalDate.now(ZoneOffset.UTC).toString().substring(0, 7); // YYYY-MM
        return buildUsage(userId, period, plan);
    }

    private UsageResponse buildUsage(String userId, String periodMonth, Plan plan) {
        long leads = 0, pages = 0, visits = 0;
        if (userId != null && !userId.isBlank()) {
            for (var c : usageCounterMapper.listByUserPeriod(userId, periodMonth)) {
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
}

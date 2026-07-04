package com.luban.backend.operatorside.service;

import com.luban.backend.shared.domain.SubscriptionAggregate;
import com.luban.backend.shared.entity.Plan;
import com.luban.backend.shared.entity.Subscription;
import com.luban.backend.shared.exception.BusinessException;
import com.luban.backend.shared.mapper.PlanMapper;
import com.luban.backend.shared.repository.SubscriptionRepository;
import org.springframework.stereotype.Service;

/**
 * 用量配额应用服务（backend-ddd-refactor plan v2 T14）。
 *
 * <p>checkAndIncrement：原子检查配额并递增（修复现状非原子超限问题）。
 * 由业务代码（LeadService 提交）调用。
 *
 * <p>改造后：
 * <ul>
 *   <li>聚合根 {@link SubscriptionAggregate#assertQuotaAvailable} 做"是否超限"决策</li>
 *   <li>原子递增用 {@link UsageCounterMapper#incrementAtomicIfUnderQuota}（单条 SQL 原子 check-and-increment，
 *       修复现状 check-then-increment 非原子的并发超限问题）</li>
 *   <li>quota=0 表示无限制（兼容历史/特殊套餐）</li>
 * </ul>
 */
@Service
public class QuotaService {

    private static final String FREE_PLAN = "free";

    private final SubscriptionRepository subscriptionRepository;
    private final PlanMapper planMapper;

    public QuotaService(SubscriptionRepository subscriptionRepository,
                        PlanMapper planMapper) {
        this.subscriptionRepository = subscriptionRepository;
        this.planMapper = planMapper;
    }

    /**
     * 原子检查配额并递增。超限抛 BusinessException(429)。
     *
     * @param userId 用户 ID（null/blank 匿名用户不限）
     * @param metric leads/pages/visits
     */
    public void checkAndIncrement(String userId, String metric) {
        if (userId == null || userId.isBlank()) return;  // 匿名用户不限

        Plan plan = resolvePlan(userId);
        int quota = quotaForMetric(plan, metric);
        if (quota <= 0) {
            // quota=0 无限制：直接原子递增（不限流）
            subscriptionRepository.incrementUsageAtomicIfUnderQuota(userId, metric, Integer.MAX_VALUE);
            return;
        }

        // 原子 check-and-increment：单条 SQL，并发安全
        long newCount = subscriptionRepository.incrementUsageAtomicIfUnderQuota(userId, metric, quota);
        // 聚合根决策（一致性校验 + 明确异常）
        SubscriptionAggregate agg = subscriptionRepository.findByUserId(userId);
        if (agg != null) {
            agg.assertQuotaAvailable(quota, newCount);
        } else {
            // 无订阅记录兜底：直接校验（避免漏检）
            if (newCount >= quota) {
                throw new BusinessException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                        "QUOTA_EXCEEDED",
                        String.format("用量超限：%s 已用 %d/%d，请升级套餐", metricLabel(metric), newCount, quota));
            }
        }
    }

    private Plan resolvePlan(String userId) {
        SubscriptionAggregate agg = subscriptionRepository.findByUserId(userId);
        if (agg == null) return planMapper.getByCode(FREE_PLAN);
        Subscription sub = agg.toSubscription();
        String planCode = sub != null ? sub.getPlanCode() : FREE_PLAN;
        Plan plan = planMapper.getByCode(planCode);
        return plan != null ? plan : planMapper.getByCode(FREE_PLAN);
    }

    private int quotaForMetric(Plan plan, String metric) {
        return switch (metric) {
            case "leads" -> plan.getQuotaLeads();
            case "pages" -> plan.getQuotaPages();
            case "visits" -> plan.getQuotaVisits();
            default -> 0;  // 未知 metric 不限流
        };
    }

    private String metricLabel(String metric) {
        return switch (metric) {
            case "leads" -> "线索数";
            case "pages" -> "页面数";
            case "visits" -> "访问量";
            default -> metric;
        };
    }
}

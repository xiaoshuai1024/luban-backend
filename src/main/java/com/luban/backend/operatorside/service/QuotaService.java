package com.luban.backend.operatorside.service;

import com.luban.backend.shared.entity.Plan;
import com.luban.backend.shared.entity.Subscription;
import com.luban.backend.shared.exception.BusinessException;
import com.luban.backend.shared.mapper.PlanMapper;
import com.luban.backend.shared.mapper.SubscriptionMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 用量配额服务（v02 billing 域，T-be-4）。
 *
 * checkAndIncrement：检查当前用量是否超限，未超限则原子递增；超限抛 429。
 * 由业务代码（LeadService 提交、PageService 创建、analytics 事件接收）调用。
 *
 * quota=0 表示无限制（兼容历史/特殊套餐）。
 */
@Service
public class QuotaService {

    private static final String FREE_PLAN = "free";
    private static final String METRIC_LEADS = "leads";
    private static final String METRIC_PAGES = "pages";

    private final UsageService usageService;
    private final SubscriptionMapper subscriptionMapper;
    private final PlanMapper planMapper;

    public QuotaService(UsageService usageService, SubscriptionMapper subscriptionMapper,
                        PlanMapper planMapper) {
        this.usageService = usageService;
        this.subscriptionMapper = subscriptionMapper;
        this.planMapper = planMapper;
    }

    /**
     * 检查并递增用量。超限抛 BusinessException(429)。
     *
     * @param userId 用户 ID
     * @param metric leads/pages/visits
     */
    public void checkAndIncrement(String userId, String metric) {
        if (userId == null || userId.isBlank()) return;  // 匿名用户不限

        Plan plan = resolvePlan(userId);
        int quota = quotaForMetric(plan, metric);
        if (quota <= 0) {
            // quota=0 无限制（或 metric 无配额约束），仅计数不限流
            usageService.increment(userId, metric);
            return;
        }

        long current = usageService.getCount(userId, metric);
        if (current >= quota) {
            throw new BusinessException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "QUOTA_EXCEEDED",
                    String.format("用量超限：%s 已用 %d/%d，请升级套餐", metricLabel(metric), current, quota));
        }
        usageService.increment(userId, metric);
    }

    private Plan resolvePlan(String userId) {
        Subscription sub = subscriptionMapper.getByUserId(userId);
        String planCode = sub != null ? sub.getPlanCode() : FREE_PLAN;
        Plan plan = planMapper.getByCode(planCode);
        return plan != null ? plan : planMapper.getByCode(FREE_PLAN);
    }

    private int quotaForMetric(Plan plan, String metric) {
        return switch (metric) {
            case METRIC_LEADS -> plan.getQuotaLeads();
            case METRIC_PAGES -> plan.getQuotaPages();
            case "visits" -> plan.getQuotaVisits();
            default -> 0;  // 未知 metric 不限流
        };
    }

    private String metricLabel(String metric) {
        return switch (metric) {
            case METRIC_LEADS -> "线索数";
            case METRIC_PAGES -> "页面数";
            case "visits" -> "访问量";
            default -> metric;
        };
    }
}

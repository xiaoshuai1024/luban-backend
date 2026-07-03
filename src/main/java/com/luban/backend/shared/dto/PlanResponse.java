package com.luban.backend.shared.dto;

import com.luban.backend.shared.entity.Plan;

import java.util.List;

/**
 * 套餐响应 DTO（GET /billing/plans）。
 * gates 反序列化为 List<String>，便于前端消费。
 */
public record PlanResponse(
    String planCode,
    String name,
    long priceMonthly,
    int quotaLeads,
    int quotaPages,
    int quotaVisits,
    List<String> gates,
    int trialDays
) {
    public static PlanResponse fromEntity(Plan plan, List<String> gates) {
        return new PlanResponse(
            plan.getPlanCode(), plan.getName(), plan.getPriceMonthly(),
            plan.getQuotaLeads(), plan.getQuotaPages(), plan.getQuotaVisits(),
            gates, plan.getTrialDays()
        );
    }
}

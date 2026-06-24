package com.luban.backend.dto;

/**
 * 用量响应 DTO（GET /billing/usage、GET /billing/me 内嵌）。
 * leads/pages/visits 为当月已用计数；quota 为对应上限（-1 = 无限）。
 */
public record UsageResponse(
    long leads,
    long pages,
    long visits,
    String period,        // YYYY-MM
    long quotaLeads,
    long quotaPages,
    long quotaVisits
) {
}

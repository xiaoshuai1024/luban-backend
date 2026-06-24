package com.luban.backend.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.luban.backend.entity.Subscription;

import java.time.Instant;

/**
 * 订阅响应 DTO（GET /billing/me、POST /billing/subscribe 返回）。
 */
public record SubscriptionResponse(
    String planCode,
    String status,
    @JsonFormat(shape = JsonFormat.Shape.STRING) Instant startedAt,
    @JsonFormat(shape = JsonFormat.Shape.STRING) Instant expiresAt,
    @JsonFormat(shape = JsonFormat.Shape.STRING) Instant trialEndsAt
) {
    public static SubscriptionResponse fromEntity(Subscription sub) {
        return new SubscriptionResponse(
            sub.getPlanCode(), sub.getStatus(), sub.getStartedAt(),
            sub.getExpiresAt(), sub.getTrialEndsAt()
        );
    }
}

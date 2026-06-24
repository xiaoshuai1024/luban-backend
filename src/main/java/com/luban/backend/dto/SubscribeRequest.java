package com.luban.backend.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 切换套餐请求 DTO（POST /billing/subscribe）。
 * 价格全 0，切换不涉及扣费；仅更新 subscription.plan_code。
 */
public record SubscribeRequest(
    @NotBlank(message = "planCode 不能为空") String planCode
) {
}

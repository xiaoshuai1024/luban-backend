package com.luban.backend.shared.dto;

import com.luban.backend.shared.entity.PaymentOrder;

import java.time.Instant;

/**
 * 支付订单响应 DTO（P-001 计费/订阅闭环）。
 *
 * <p>避免 Controller 直接返回 {@link PaymentOrder} entity（ArchUnit 守护
 * controllers_should_not_reference_entities）。
 */
public record PaymentOrderResponse(
        String orderId,
        String userId,
        String planCode,
        long amount,
        String currency,
        String channel,
        String status,
        String payUrl,
        Instant createdAt,
        Instant paidAt
) {
    public static PaymentOrderResponse fromEntity(PaymentOrder o) {
        return new PaymentOrderResponse(
                o.getId(), o.getUserId(), o.getPlanCode(), o.getAmount(),
                o.getCurrency(), o.getChannel(), o.getStatus(),
                o.getPayUrl(), o.getCreatedAt(), o.getPaidAt()
        );
    }
}

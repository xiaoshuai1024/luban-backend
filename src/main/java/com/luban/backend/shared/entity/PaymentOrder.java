package com.luban.backend.shared.entity;

import java.time.Instant;

/**
 * payment_orders 表实体（P-001 计费/订阅闭环）。
 *
 * <p>记录用户升级套餐时的支付订单。内测期 amount=0（直通 PAID），
 * 未来接真实微信/支付宝时记录支付链接/平台响应。
 *
 * <p>状态机：PENDING → PAID / FAILED / CANCELLED
 */
public class PaymentOrder {
    private String id;
    private String userId;
    private String planCode;
    private long amount;          // 分（避免浮点）
    private String currency;
    private String channel;       // WECHAT / ALIPAY
    private String status;        // PENDING / PAID / FAILED / CANCELLED
    private String payUrl;
    private String rawResponse;
    private Instant createdAt;
    private Instant paidAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getPlanCode() { return planCode; }
    public void setPlanCode(String planCode) { this.planCode = planCode; }

    public long getAmount() { return amount; }
    public void setAmount(long amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPayUrl() { return payUrl; }
    public void setPayUrl(String payUrl) { this.payUrl = payUrl; }

    public String getRawResponse() { return rawResponse; }
    public void setRawResponse(String rawResponse) { this.rawResponse = rawResponse; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getPaidAt() { return paidAt; }
    public void setPaidAt(Instant paidAt) { this.paidAt = paidAt; }
}

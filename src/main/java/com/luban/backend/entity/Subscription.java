package com.luban.backend.entity;

import java.time.Instant;

/**
 * 用户订阅（v02 billing 域）；表 subscriptions。user_id 为主键（一用户一订阅）。
 * status: active/trialing/expired；trial 字段仅在试用期为非空。
 */
public class Subscription {
    private String userId;
    private String planCode;
    private String status;            // active/trialing/expired
    private Instant startedAt;
    private Instant expiresAt;
    private Instant trialStartedAt;
    private Instant trialEndsAt;

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getPlanCode() { return planCode; }
    public void setPlanCode(String planCode) { this.planCode = planCode; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getTrialStartedAt() { return trialStartedAt; }
    public void setTrialStartedAt(Instant trialStartedAt) { this.trialStartedAt = trialStartedAt; }
    public Instant getTrialEndsAt() { return trialEndsAt; }
    public void setTrialEndsAt(Instant trialEndsAt) { this.trialEndsAt = trialEndsAt; }
}

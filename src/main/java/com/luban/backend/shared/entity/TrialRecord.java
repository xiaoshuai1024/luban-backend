package com.luban.backend.shared.entity;

import java.time.Instant;

/**
 * 试用记录（v02 billing 域）；表 trial_records。user_id 为主键。
 * trial_plan_code 为试用套餐（如 starter）；ends_at 到期后由 TrialScheduler 触发降级，
 * converted_to 记录降级去向（通常为 free）。
 */
public class TrialRecord {
    private String userId;
    private String trialPlanCode;
    private Instant startedAt;
    private Instant endsAt;
    private String convertedTo;     // 降级去向 plan_code（null = 尚未降级）

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getTrialPlanCode() { return trialPlanCode; }
    public void setTrialPlanCode(String trialPlanCode) { this.trialPlanCode = trialPlanCode; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getEndsAt() { return endsAt; }
    public void setEndsAt(Instant endsAt) { this.endsAt = endsAt; }
    public String getConvertedTo() { return convertedTo; }
    public void setConvertedTo(String convertedTo) { this.convertedTo = convertedTo; }
}

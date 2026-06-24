package com.luban.backend.entity;

/**
 * 月度用量计数（v02 billing 域）；表 usage_counters。
 * (user_id, period_month, metric) 唯一；count 原子累加（INSERT ON DUPLICATE KEY UPDATE）。
 * metric: leads/pages/visits。
 */
public class UsageCounter {
    private String id;
    private String userId;
    private String periodMonth;    // YYYY-MM
    private String metric;         // leads/pages/visits
    private long count;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getPeriodMonth() { return periodMonth; }
    public void setPeriodMonth(String periodMonth) { this.periodMonth = periodMonth; }
    public String getMetric() { return metric; }
    public void setMetric(String metric) { this.metric = metric; }
    public long getCount() { return count; }
    public void setCount(long count) { this.count = count; }
}

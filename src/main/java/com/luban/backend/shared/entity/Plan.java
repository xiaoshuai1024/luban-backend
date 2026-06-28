package com.luban.backend.shared.entity;

/**
 * 套餐定义（v02 billing 域）；表 plans。三档 Free/Starter/Growth，价格全 0。
 * gates 为 JSON 字符串（放行的 gate_key 集合，如 ["lead_capture","analytics"]）。
 */
public class Plan {
    private String planCode;
    private String name;
    private long priceMonthly;
    private int quotaLeads;
    private int quotaPages;
    private int quotaVisits;
    private String gates;        // JSON 数组字符串：放行的 gate_key 集合
    private int trialDays;
    private int sortOrder;

    public String getPlanCode() { return planCode; }
    public void setPlanCode(String planCode) { this.planCode = planCode; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public long getPriceMonthly() { return priceMonthly; }
    public void setPriceMonthly(long priceMonthly) { this.priceMonthly = priceMonthly; }
    public int getQuotaLeads() { return quotaLeads; }
    public void setQuotaLeads(int quotaLeads) { this.quotaLeads = quotaLeads; }
    public int getQuotaPages() { return quotaPages; }
    public void setQuotaPages(int quotaPages) { this.quotaPages = quotaPages; }
    public int getQuotaVisits() { return quotaVisits; }
    public void setQuotaVisits(int quotaVisits) { this.quotaVisits = quotaVisits; }
    public String getGates() { return gates; }
    public void setGates(String gates) { this.gates = gates; }
    public int getTrialDays() { return trialDays; }
    public void setTrialDays(int trialDays) { this.trialDays = trialDays; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}

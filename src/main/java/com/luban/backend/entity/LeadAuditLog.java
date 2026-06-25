package com.luban.backend.entity;

import java.time.Instant;

/**
 * LeadAuditLog entity; table lead_audit_logs.
 * 记录线索敏感操作审计：VIEW_CONTACT（解密查看）/ STATUS_TRANSIT（状态转移）。
 * plan §3.1 审计口径 + §2.2 链路 L3。
 */
public class LeadAuditLog {
    private String id;
    private String siteId;
    private String leadId;
    private String actorId;
    private String action;
    private String detail;
    private Instant createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSiteId() { return siteId; }
    public void setSiteId(String siteId) { this.siteId = siteId; }
    public String getLeadId() { return leadId; }
    public void setLeadId(String leadId) { this.leadId = leadId; }
    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

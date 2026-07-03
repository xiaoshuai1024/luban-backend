package com.luban.backend.shared.entity;

import java.time.Instant;

/**
 * AB 分桶（v02 ab 域）；表 ab_assignments。
 * visitor_id + experiment_id 唯一 → variant_id（一致性哈希稳定分桶）。
 */
public class AbAssignment {
    private String id;
    private String visitorId;
    private String experimentId;
    private String variantId;
    private Instant assignedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getVisitorId() { return visitorId; }
    public void setVisitorId(String visitorId) { this.visitorId = visitorId; }
    public String getExperimentId() { return experimentId; }
    public void setExperimentId(String experimentId) { this.experimentId = experimentId; }
    public String getVariantId() { return variantId; }
    public void setVariantId(String variantId) { this.variantId = variantId; }
    public Instant getAssignedAt() { return assignedAt; }
    public void setAssignedAt(Instant assignedAt) { this.assignedAt = assignedAt; }
}

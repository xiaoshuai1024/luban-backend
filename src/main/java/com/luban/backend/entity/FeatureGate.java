package com.luban.backend.entity;

import java.time.Instant;

/**
 * FeatureGate entity; table feature_gates (复合主键 site_id + gate_key)。
 * plan §3.5：lead_capture / realtime_collab / page_versioning / poster_export。
 * 关闭时对应能力降级或隐藏。
 */
public class FeatureGate {
    private String siteId;
    private String gateKey;
    private boolean enabled;
    private Instant updatedAt;

    public String getSiteId() { return siteId; }
    public void setSiteId(String siteId) { this.siteId = siteId; }
    public String getGateKey() { return gateKey; }
    public void setGateKey(String gateKey) { this.gateKey = gateKey; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

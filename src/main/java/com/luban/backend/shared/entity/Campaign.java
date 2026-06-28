package com.luban.backend.shared.entity;

import java.time.Instant;

/**
 * Campaign entity; table campaigns.
 *
 * <p>营销活动聚合根的载体实体。状态机：planned → active → completed/cancelled。
 * 归属：app-deeplink-backend-arch plan T7。
 */
public class Campaign {
    private String id;
    private String siteId;
    private String name;
    private Instant startAt;
    private Instant endAt;
    /** planned / active / completed / cancelled */
    private String status;
    private Instant createdAt;
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSiteId() { return siteId; }
    public void setSiteId(String siteId) { this.siteId = siteId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Instant getStartAt() { return startAt; }
    public void setStartAt(Instant startAt) { this.startAt = startAt; }
    public Instant getEndAt() { return endAt; }
    public void setEndAt(Instant endAt) { this.endAt = endAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

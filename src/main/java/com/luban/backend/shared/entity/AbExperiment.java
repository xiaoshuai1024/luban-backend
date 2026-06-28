package com.luban.backend.shared.entity;

import java.time.Instant;

/**
 * AB 实验（v02 ab 域）；表 ab_experiments。
 * 单页单 running 约束（应用层校验）；status = draft/running/paused/ended。
 */
public class AbExperiment {
    private String id;
    private String siteId;
    private String pageId;
    private String name;
    private String status;       // draft/running/paused/ended
    private int trafficPct;      // 流量百分比 0-100
    private Instant startAt;
    private Instant endAt;
    private Instant createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSiteId() { return siteId; }
    public void setSiteId(String siteId) { this.siteId = siteId; }
    public String getPageId() { return pageId; }
    public void setPageId(String pageId) { this.pageId = pageId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getTrafficPct() { return trafficPct; }
    public void setTrafficPct(int trafficPct) { this.trafficPct = trafficPct; }
    public Instant getStartAt() { return startAt; }
    public void setStartAt(Instant startAt) { this.startAt = startAt; }
    public Instant getEndAt() { return endAt; }
    public void setEndAt(Instant endAt) { this.endAt = endAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

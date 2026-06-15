package com.luban.backend.entity;

import java.time.Instant;

/**
 * PageVersion entity; table page_versions. schemaJson holds 发布时刻的页面 schema 快照。
 * 每次 published 状态变更 → 自增 version，快照 schema_json（plan §3.4）。
 */
public class PageVersion {
    private String id;
    private String siteId;
    private String pageId;
    private int version;
    private String schemaJson;
    private String operatorId;
    private Instant createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSiteId() { return siteId; }
    public void setSiteId(String siteId) { this.siteId = siteId; }
    public String getPageId() { return pageId; }
    public void setPageId(String pageId) { this.pageId = pageId; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public String getSchemaJson() { return schemaJson; }
    public void setSchemaJson(String schemaJson) { this.schemaJson = schemaJson; }
    public String getOperatorId() { return operatorId; }
    public void setOperatorId(String operatorId) { this.operatorId = operatorId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

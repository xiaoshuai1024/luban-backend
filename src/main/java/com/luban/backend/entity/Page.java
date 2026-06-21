package com.luban.backend.entity;

import java.time.Instant;

/**
 * Page entity; table pages. schemaJson holds JSON string.
 *
 * V2-T2: seoJson 持久化页面级 SEO（PageSchema.seo 结构）。
 */
public class Page {
    private String id;
    private String siteId;
    private String name;
    private String path;
    private String status;
    private String schemaJson;
    /** V2-T2 页面级 SEO JSON title/description/keywords/og/canonical/noIndex */
    private String seoJson;
    private Instant createdAt;
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSiteId() { return siteId; }
    public void setSiteId(String siteId) { this.siteId = siteId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSchemaJson() { return schemaJson; }
    public void setSchemaJson(String schemaJson) { this.schemaJson = schemaJson; }
    public String getSeoJson() { return seoJson; }
    public void setSeoJson(String seoJson) { this.seoJson = seoJson; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

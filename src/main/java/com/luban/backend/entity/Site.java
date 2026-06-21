package com.luban.backend.entity;

import java.time.Instant;

/**
 * Site entity; table sites.
 */
public class Site {
    private String id;
    private String name;
    private String slug;
    private String baseUrl;
    private String status;
    /** V2-T2 站点级 SEO JSON */
    private String seoJson;
    /** V2-T10 站点级分析埋点配置 JSON（GA4/百度统计/Facebook Pixel） */
    private String analyticsJson;
    private Instant createdAt;
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSeoJson() { return seoJson; }
    public void setSeoJson(String seoJson) { this.seoJson = seoJson; }
    public String getAnalyticsJson() { return analyticsJson; }
    public void setAnalyticsJson(String analyticsJson) { this.analyticsJson = analyticsJson; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

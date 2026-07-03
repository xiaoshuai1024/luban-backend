package com.luban.backend.shared.entity;

import java.time.Instant;

/**
 * Template entity; table templates.
 *
 * <p>模板市场目录条目（聚合根 TemplateAggregate 的持久化形态）。
 * 状态机：draft → published → archived；featured 为 published 的特殊展示态。
 * authorId 为 null 表示官方模板（预留 UGC：用户贡献时填 userId）。
 * 归属：template-marketplace plan。
 */
public class Template {
    private String id;
    /** 市场唯一短标识（url-friendly） */
    private String slug;
    private String name;
    /** blank/saas/ecommerce/education/blog/landing/portfolio */
    private String category;
    private String description;
    /** emoji 或 URL */
    private String thumbnail;
    /** 贡献者（null=官方模板，预留 UGC） */
    private String authorId;
    /** draft/published/archived/featured */
    private String status;
    private Integer latestVersion;
    private Instant createdAt;
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getThumbnail() { return thumbnail; }
    public void setThumbnail(String thumbnail) { this.thumbnail = thumbnail; }
    public String getAuthorId() { return authorId; }
    public void setAuthorId(String authorId) { this.authorId = authorId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getLatestVersion() { return latestVersion; }
    public void setLatestVersion(Integer latestVersion) { this.latestVersion = latestVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

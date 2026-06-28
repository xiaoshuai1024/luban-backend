package com.luban.backend.shared.entity;

import java.time.Instant;

/**
 * 发布快照实体；表 published_pages。
 *
 * <p>P0 发布闭环：与 {@link Page} 草稿表分离。发布时从 pages 拷贝当前草稿到此表，
 * 公开接口读此表而非 pages。下线时删除此表记录。
 */
public class PublishedPage {
    private String id;
    private String pageId;
    private String siteId;
    private String name;
    private String path;
    private String schemaJson;
    private String seoJson;
    private Instant publishedAt;
    private String publishedBy;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getPageId() { return pageId; }
    public void setPageId(String pageId) { this.pageId = pageId; }
    public String getSiteId() { return siteId; }
    public void setSiteId(String siteId) { this.siteId = siteId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getSchemaJson() { return schemaJson; }
    public void setSchemaJson(String schemaJson) { this.schemaJson = schemaJson; }
    public String getSeoJson() { return seoJson; }
    public void setSeoJson(String seoJson) { this.seoJson = seoJson; }
    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }
    public String getPublishedBy() { return publishedBy; }
    public void setPublishedBy(String publishedBy) { this.publishedBy = publishedBy; }
}

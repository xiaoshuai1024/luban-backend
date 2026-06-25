package com.luban.backend.entity;

import java.time.Instant;

/**
 * V2-T7 Collection 内容集合实体。
 * fieldSchemaJson 定义字段结构（JSON），collection_items 表存具体内容项。
 */
public class ContentCollection {
    private String id;
    private String siteId;
    private String name;
    private String fieldSchemaJson;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSiteId() { return siteId; }
    public void setSiteId(String siteId) { this.siteId = siteId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getFieldSchemaJson() { return fieldSchemaJson; }
    public void setFieldSchemaJson(String fieldSchemaJson) { this.fieldSchemaJson = fieldSchemaJson; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

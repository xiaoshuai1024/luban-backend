package com.luban.backend.shared.entity;

import java.time.Instant;

/**
 * Datasource entity; table datasources. configJson holds JSON string.
 *
 * <p>Aligned with luban-backend-go internal/model/datasource.go (Config json.RawMessage).
 * {@code type} whitelist ({@code static|api}) is enforced in the service layer, not here.
 */
public class Datasource {
    private String id;
    private String siteId;
    private String name;
    private String type;
    private String configJson;
    private Instant createdAt;
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSiteId() { return siteId; }
    public void setSiteId(String siteId) { this.siteId = siteId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getConfigJson() { return configJson; }
    public void setConfigJson(String configJson) { this.configJson = configJson; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

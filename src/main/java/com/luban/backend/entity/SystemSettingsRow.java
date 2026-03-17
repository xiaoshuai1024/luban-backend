package com.luban.backend.entity;

import java.time.Instant;

/**
 * System settings single row; table system_settings (id=1).
 */
public class SystemSettingsRow {
    private Integer id;
    private String dataJson;
    private Instant updatedAt;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getDataJson() { return dataJson; }
    public void setDataJson(String dataJson) { this.dataJson = dataJson; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

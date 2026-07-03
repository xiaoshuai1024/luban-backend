package com.luban.backend.shared.entity;

import java.time.Instant;

/**
 * TemplateVersion entity; table template_versions.
 *
 * <p>模板版本快照（schema_json 完整 PageSchema）。模板每次发布产生新版本，
 * 市场展示 published 最新版。类比 page_versions 的 createSnapshot 范式。
 */
public class TemplateVersion {
    private String id;
    private String templateId;
    private Integer version;
    /** 完整 PageSchema JSON 字符串 */
    private String schemaJson;
    private String changeNote;
    private Instant createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTemplateId() { return templateId; }
    public void setTemplateId(String templateId) { this.templateId = templateId; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getSchemaJson() { return schemaJson; }
    public void setSchemaJson(String schemaJson) { this.schemaJson = schemaJson; }
    public String getChangeNote() { return changeNote; }
    public void setChangeNote(String changeNote) { this.changeNote = changeNote; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

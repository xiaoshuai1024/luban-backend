package com.luban.backend.shared.entity;

import java.time.Instant;

/**
 * TemplateInstallation entity; table template_installations.
 *
 * <p>安装记录（审计 + 计数）：记录谁把哪个模板的哪个版本装到了哪个 site/page。
 * 用于安装量统计与后续可能的"我安装过的模板"查询。
 */
public class TemplateInstallation {
    private String id;
    private String templateId;
    private Integer version;
    private String siteId;
    private String pageId;
    private String installerId;
    private Instant createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTemplateId() { return templateId; }
    public void setTemplateId(String templateId) { this.templateId = templateId; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getSiteId() { return siteId; }
    public void setSiteId(String siteId) { this.siteId = siteId; }
    public String getPageId() { return pageId; }
    public void setPageId(String pageId) { this.pageId = pageId; }
    public String getInstallerId() { return installerId; }
    public void setInstallerId(String installerId) { this.installerId = installerId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

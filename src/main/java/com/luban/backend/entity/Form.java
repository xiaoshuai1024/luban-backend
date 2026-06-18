package com.luban.backend.entity;

import java.time.Instant;

/**
 * Form entity; table forms. 描述一张留资表单的字段、提交配置、去重与防刷策略。
 */
public class Form {
    private String id;
    private String siteId;
    private String pageId;
    private String name;
    private String fieldSchemaJson;    // 字段定义 JSON
    private String submitConfigJson;   // 提交行为配置 JSON（successAction/redirect/message/captcha）
    private String dedupKeysJson;      // 去重键 JSON，如 ["phone"]
    private int dedupWindow;           // 去重时间窗(秒)，默认 86400
    private String dedupPolicy;        // reject/mark/overwrite/merge
    private String antiSpamJson;       // 防刷配置 JSON
    private String status;             // active/disabled
    private Instant createdAt;
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSiteId() { return siteId; }
    public void setSiteId(String siteId) { this.siteId = siteId; }
    public String getPageId() { return pageId; }
    public void setPageId(String pageId) { this.pageId = pageId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getFieldSchemaJson() { return fieldSchemaJson; }
    public void setFieldSchemaJson(String fieldSchemaJson) { this.fieldSchemaJson = fieldSchemaJson; }
    public String getSubmitConfigJson() { return submitConfigJson; }
    public void setSubmitConfigJson(String submitConfigJson) { this.submitConfigJson = submitConfigJson; }
    public String getDedupKeysJson() { return dedupKeysJson; }
    public void setDedupKeysJson(String dedupKeysJson) { this.dedupKeysJson = dedupKeysJson; }
    public int getDedupWindow() { return dedupWindow; }
    public void setDedupWindow(int dedupWindow) { this.dedupWindow = dedupWindow; }
    public String getDedupPolicy() { return dedupPolicy; }
    public void setDedupPolicy(String dedupPolicy) { this.dedupPolicy = dedupPolicy; }
    public String getAntiSpamJson() { return antiSpamJson; }
    public void setAntiSpamJson(String antiSpamJson) { this.antiSpamJson = antiSpamJson; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

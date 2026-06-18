package com.luban.backend.entity;

import java.time.Instant;

/**
 * Lead entity; table leads. contactJson 持有 AES 加密后的联系人 JSON（phone/email 等）。
 */
public class Lead {
    private String id;
    private String siteId;
    private String formId;
    private String pageId;
    private String channelId;
    private String contactJson;   // AES 加密后的联系人 JSON
    private String utmJson;        // 来源 UTM JSON
    private String status;         // new/assigned/contacting/converted/invalid/lost
    private String assigneeId;
    private String dedupHash;
    private String sourceIp;
    private String visitorId;
    private Instant convertedAt;
    private Instant createdAt;
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSiteId() { return siteId; }
    public void setSiteId(String siteId) { this.siteId = siteId; }
    public String getFormId() { return formId; }
    public void setFormId(String formId) { this.formId = formId; }
    public String getPageId() { return pageId; }
    public void setPageId(String pageId) { this.pageId = pageId; }
    public String getChannelId() { return channelId; }
    public void setChannelId(String channelId) { this.channelId = channelId; }
    public String getContactJson() { return contactJson; }
    public void setContactJson(String contactJson) { this.contactJson = contactJson; }
    public String getUtmJson() { return utmJson; }
    public void setUtmJson(String utmJson) { this.utmJson = utmJson; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getAssigneeId() { return assigneeId; }
    public void setAssigneeId(String assigneeId) { this.assigneeId = assigneeId; }
    public String getDedupHash() { return dedupHash; }
    public void setDedupHash(String dedupHash) { this.dedupHash = dedupHash; }
    public String getSourceIp() { return sourceIp; }
    public void setSourceIp(String sourceIp) { this.sourceIp = sourceIp; }
    public String getVisitorId() { return visitorId; }
    public void setVisitorId(String visitorId) { this.visitorId = visitorId; }
    public Instant getConvertedAt() { return convertedAt; }
    public void setConvertedAt(Instant convertedAt) { this.convertedAt = convertedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

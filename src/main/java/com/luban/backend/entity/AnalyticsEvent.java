package com.luban.backend.entity;

import java.time.Instant;

/**
 * 原始事件（v02 analytics 域）；表 analytics_events。
 * source_ip 仅存 AES 哈希（source_ip_hashed），不存原值（方案 §6.2）。
 */
public class AnalyticsEvent {
    private String id;
    private String siteId;
    private String visitorId;
    private String sessionId;
    private String eventType;        // page_view/form_expose/form_submit
    private String eventPayload;     // JSON 字符串
    private String pageId;
    private String variantId;
    private String utmJson;          // JSON 字符串
    private Instant clientTs;
    private Instant serverTs;        // DB 默认 CURRENT_TIMESTAMP(3)
    private String sourceIpHashed;   // AES 加密后的 source_ip

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSiteId() { return siteId; }
    public void setSiteId(String siteId) { this.siteId = siteId; }
    public String getVisitorId() { return visitorId; }
    public void setVisitorId(String visitorId) { this.visitorId = visitorId; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getEventPayload() { return eventPayload; }
    public void setEventPayload(String eventPayload) { this.eventPayload = eventPayload; }
    public String getPageId() { return pageId; }
    public void setPageId(String pageId) { this.pageId = pageId; }
    public String getVariantId() { return variantId; }
    public void setVariantId(String variantId) { this.variantId = variantId; }
    public String getUtmJson() { return utmJson; }
    public void setUtmJson(String utmJson) { this.utmJson = utmJson; }
    public Instant getClientTs() { return clientTs; }
    public void setClientTs(Instant clientTs) { this.clientTs = clientTs; }
    public Instant getServerTs() { return serverTs; }
    public void setServerTs(Instant serverTs) { this.serverTs = serverTs; }
    public String getSourceIpHashed() { return sourceIpHashed; }
    public void setSourceIpHashed(String sourceIpHashed) { this.sourceIpHashed = sourceIpHashed; }
}

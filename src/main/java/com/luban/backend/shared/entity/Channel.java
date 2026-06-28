package com.luban.backend.shared.entity;

import java.time.Instant;

/**
 * Channel entity; table channels.
 *
 * <p>渠道/短链实体。每个 Channel 绑定一个目标页面，携带 UTM 模板用于归因。
 * 状态机：active ↔ inactive（停用短链返回 410 Gone）。
 * 归属：app-deeplink-backend-arch plan T7。
 */
public class Channel {
    private String id;
    private String siteId;
    /** 所属 campaign（可空：channel 可独立存在不挂活动） */
    private String campaignId;
    /** 短码（运营指定或系统生成 base62 6 位） */
    private String code;
    /** qrcode / h5 / social / ad / miniapp */
    private String type;
    /** UTM 模板 JSON：{utm_source, utm_medium, ...} */
    private String utmTemplate;
    /** 短链路径（= code，唯一） */
    private String shortUrl;
    /** 目标页面 ID（page.site_id 必须 = channel.site_id） */
    private String targetPageId;
    /** active / inactive */
    private String status;
    private Instant createdAt;
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSiteId() { return siteId; }
    public void setSiteId(String siteId) { this.siteId = siteId; }
    public String getCampaignId() { return campaignId; }
    public void setCampaignId(String campaignId) { this.campaignId = campaignId; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getUtmTemplate() { return utmTemplate; }
    public void setUtmTemplate(String utmTemplate) { this.utmTemplate = utmTemplate; }
    public String getShortUrl() { return shortUrl; }
    public void setShortUrl(String shortUrl) { this.shortUrl = shortUrl; }
    public String getTargetPageId() { return targetPageId; }
    public void setTargetPageId(String targetPageId) { this.targetPageId = targetPageId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

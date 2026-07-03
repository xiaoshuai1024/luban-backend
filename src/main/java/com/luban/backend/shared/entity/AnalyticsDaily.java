package com.luban.backend.shared.entity;

import java.time.LocalDate;

/**
 * 预聚合日表（v02 analytics 域）；表 analytics_daily。
 * site_id + date + page_id + variant_id 唯一，聚合 views/submissions/conversions。
 */
public class AnalyticsDaily {
    private String id;
    private String siteId;
    private LocalDate date;
    private String pageId;
    private String variantId;
    private long views;
    private long submissions;
    private long conversions;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSiteId() { return siteId; }
    public void setSiteId(String siteId) { this.siteId = siteId; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public String getPageId() { return pageId; }
    public void setPageId(String pageId) { this.pageId = pageId; }
    public String getVariantId() { return variantId; }
    public void setVariantId(String variantId) { this.variantId = variantId; }
    public long getViews() { return views; }
    public void setViews(long views) { this.views = views; }
    public long getSubmissions() { return submissions; }
    public void setSubmissions(long submissions) { this.submissions = submissions; }
    public long getConversions() { return conversions; }
    public void setConversions(long conversions) { this.conversions = conversions; }
}

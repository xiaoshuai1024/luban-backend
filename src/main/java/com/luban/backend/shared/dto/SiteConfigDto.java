package com.luban.backend.shared.dto;

/**
 * 站点配置只读 DTO（publicside 站点配置接口用）。
 *
 * <p>从 controller 经 {@code SiteConfigReadPort} 获取，避免 publicside 直连 SiteMapper/Site entity。
 * 携带 website 注入第三方分析 SDK 所需的最小字段集。
 *
 * @param name          站点名
 * @param baseUrl       站点 base url
 * @param analyticsJson 分析配置 JSON（GA4/百度统计/Facebook Pixel）
 */
public record SiteConfigDto(String name, String baseUrl, String analyticsJson) {}

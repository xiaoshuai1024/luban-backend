package com.luban.backend.shared.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;

/**
 * 站点创建/更新请求。
 * V2-T2: seo（站点级 SEO，可选）。
 * V2-T10: analytics（站点级埋点配置，可选；GA4/百度统计/Facebook Pixel）。
 */
public record SiteCreateRequest(
    @NotBlank String name,
    @NotBlank String slug,
    String baseUrl,
    String status,
    JsonNode seo,
    JsonNode analytics
) {}

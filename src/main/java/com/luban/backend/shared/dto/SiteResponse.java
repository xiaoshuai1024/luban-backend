package com.luban.backend.shared.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luban.backend.shared.entity.Site;

import java.time.Instant;

/**
 * V2-T2: seo 站点级 SEO；V2-T10: analytics 站点级埋点配置。
 */
public record SiteResponse(
    String id,
    String name,
    String slug,
    String baseUrl,
    String status,
    JsonNode seo,
    JsonNode analytics,
    @JsonFormat(shape = JsonFormat.Shape.STRING) Instant createdAt,
    @JsonFormat(shape = JsonFormat.Shape.STRING) Instant updatedAt
) {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static SiteResponse fromEntity(Site s) {
        if (s == null) return null;
        JsonNode seoNode = parseJson(s.getSeoJson());
        JsonNode analyticsNode = parseJson(s.getAnalyticsJson());
        return new SiteResponse(
            s.getId(),
            s.getName(),
            s.getSlug(),
            s.getBaseUrl(),
            s.getStatus(),
            seoNode,
            analyticsNode,
            s.getCreatedAt(),
            s.getUpdatedAt()
        );
    }

    private static JsonNode parseJson(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        try {
            return MAPPER.readTree(raw);
        } catch (Exception ignored) {
            return null;
        }
    }
}

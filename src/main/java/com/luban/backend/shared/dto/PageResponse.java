package com.luban.backend.shared.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luban.backend.shared.entity.Page;

import java.time.Instant;

/**
 * V2-T2: seo 透出（页面级 SEO，website 注入 useSeoMeta）。
 */
public record PageResponse(
    String id,
    String siteId,
    String name,
    String path,
    String status,
    JsonNode schema,
    JsonNode seo,
    @JsonFormat(shape = JsonFormat.Shape.STRING) Instant createdAt,
    @JsonFormat(shape = JsonFormat.Shape.STRING) Instant updatedAt
) {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static PageResponse fromEntity(Page p) {
        if (p == null) return null;
        JsonNode schemaNode = null;
        if (p.getSchemaJson() != null && !p.getSchemaJson().isEmpty()) {
            try {
                schemaNode = MAPPER.readTree(p.getSchemaJson());
            } catch (Exception ignored) {
                schemaNode = MAPPER.createObjectNode();
            }
        }
        JsonNode seoNode = null;
        if (p.getSeoJson() != null && !p.getSeoJson().isEmpty()) {
            try {
                seoNode = MAPPER.readTree(p.getSeoJson());
            } catch (Exception ignored) {
                seoNode = null;
            }
        }
        return new PageResponse(
            p.getId(),
            p.getSiteId(),
            p.getName(),
            p.getPath(),
            p.getStatus(),
            schemaNode,
            seoNode,
            p.getCreatedAt(),
            p.getUpdatedAt()
        );
    }
}

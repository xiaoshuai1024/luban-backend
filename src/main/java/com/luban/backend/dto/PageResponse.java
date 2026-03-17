package com.luban.backend.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luban.backend.entity.Page;

import java.time.Instant;

public record PageResponse(
    String id,
    String siteId,
    String name,
    String path,
    String status,
    JsonNode schema,
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
        return new PageResponse(
            p.getId(),
            p.getSiteId(),
            p.getName(),
            p.getPath(),
            p.getStatus(),
            schemaNode,
            p.getCreatedAt(),
            p.getUpdatedAt()
        );
    }
}

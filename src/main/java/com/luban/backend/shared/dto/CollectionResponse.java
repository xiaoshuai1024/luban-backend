package com.luban.backend.shared.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luban.backend.shared.entity.ContentCollection;

import java.time.Instant;

/**
 * V2-T7 Collection 响应。
 */
public record CollectionResponse(
    String id,
    String siteId,
    String name,
    JsonNode fieldSchema,
    String status,
    @JsonFormat(shape = JsonFormat.Shape.STRING) Instant createdAt,
    @JsonFormat(shape = JsonFormat.Shape.STRING) Instant updatedAt
) {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static CollectionResponse fromEntity(ContentCollection c) {
        if (c == null) return null;
        JsonNode schemaNode = null;
        if (c.getFieldSchemaJson() != null && !c.getFieldSchemaJson().isEmpty()) {
            try {
                schemaNode = MAPPER.readTree(c.getFieldSchemaJson());
            } catch (Exception ignored) {
                schemaNode = MAPPER.createObjectNode();
            }
        }
        return new CollectionResponse(
            c.getId(),
            c.getSiteId(),
            c.getName(),
            schemaNode,
            c.getStatus(),
            c.getCreatedAt(),
            c.getUpdatedAt()
        );
    }
}

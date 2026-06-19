package com.luban.backend.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luban.backend.entity.Datasource;

import java.time.Instant;

/**
 * Datasource API response. {@code config} is exposed as a nested JSON object (not a
 * raw string) to match the engine/bff contract; the raw text is parsed from
 * {@code configJson} here (mirrors PageResponse handling of schema_json).
 *
 * <p>Sensitive subfields (e.g. config.headers credentials) are intentionally NOT
 * redacted at this layer — they are site-owned admin-managed resources. Per the
 * plan §敏感字段, logging is scrubbed downstream; the API surface keeps the object
 * intact so the editor can render existing config.
 */
public record DatasourceResponse(
    String id,
    String siteId,
    String name,
    String type,
    JsonNode config,
    @JsonFormat(shape = JsonFormat.Shape.STRING) Instant createdAt,
    @JsonFormat(shape = JsonFormat.Shape.STRING) Instant updatedAt
) {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static DatasourceResponse fromEntity(Datasource d) {
        if (d == null) return null;
        JsonNode configNode = null;
        if (d.getConfigJson() != null && !d.getConfigJson().isEmpty()) {
            try {
                configNode = MAPPER.readTree(d.getConfigJson());
            } catch (Exception ignored) {
                configNode = MAPPER.createObjectNode();
            }
        }
        return new DatasourceResponse(
            d.getId(),
            d.getSiteId(),
            d.getName(),
            d.getType(),
            configNode,
            d.getCreatedAt(),
            d.getUpdatedAt()
        );
    }
}

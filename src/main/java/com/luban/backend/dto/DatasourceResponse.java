package com.luban.backend.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.luban.backend.entity.Datasource;

import java.time.Instant;

/**
 * Datasource API response. {@code config} is exposed as a nested JSON object (not a
 * raw string) to match the engine/bff contract; the raw text is parsed from
 * {@code configJson} here (mirrors PageResponse handling of schema_json).
 *
 * <p>Sensitive subfields are redacted here: {@code config.headers} credential values
 * are masked to {@code "***"} (key names preserved so the editor knows which headers
 * are configured). GET /datasources is open to any authenticated user (RequireUser),
 * so leaking raw credentials would expose third-party API keys / Bearer tokens to
 * every tenant member. Masking happens on a deep copy — the source entity is never
 * mutated.
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

    /** Sentinel value replacing any credential held under config.headers. */
    private static final String MASKED = "***";

    public static DatasourceResponse fromEntity(Datasource d) {
        if (d == null) return null;
        JsonNode configNode = null;
        if (d.getConfigJson() != null && !d.getConfigJson().isEmpty()) {
            try {
                // deepCopy so we never mutate a shared/cached node tree
                configNode = MAPPER.readTree(d.getConfigJson()).deepCopy();
            } catch (Exception ignored) {
                configNode = MAPPER.createObjectNode();
            }
            maskHeaders(configNode);
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

    /**
     * Replace every value under {@code config.headers} with {@link #MASKED}. The key
     * set is preserved so the editor can render "these headers are configured" without
     * exposing secrets. No-op when headers is absent or not an object.
     */
    private static void maskHeaders(JsonNode configNode) {
        if (configNode == null || !configNode.isObject()) return;
        JsonNode headers = configNode.get("headers");
        if (headers == null || !headers.isObject()) return;
        ObjectNode headersObj = (ObjectNode) headers;
        headersObj.fieldNames().forEachRemaining(key -> headersObj.put(key, MASKED));
    }
}

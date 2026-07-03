package com.luban.backend.shared.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luban.backend.shared.entity.ContentCollectionItem;

import java.time.Instant;

/**
 * V2-T7 CollectionItem 响应。
 */
public record CollectionItemResponse(
    String id,
    String collectionId,
    JsonNode data,
    String status,
    @JsonFormat(shape = JsonFormat.Shape.STRING) Instant createdAt,
    @JsonFormat(shape = JsonFormat.Shape.STRING) Instant updatedAt
) {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static CollectionItemResponse fromEntity(ContentCollectionItem it) {
        if (it == null) return null;
        JsonNode dataNode = null;
        if (it.getDataJson() != null && !it.getDataJson().isEmpty()) {
            try {
                dataNode = MAPPER.readTree(it.getDataJson());
            } catch (Exception ignored) {
                dataNode = MAPPER.createObjectNode();
            }
        }
        return new CollectionItemResponse(
            it.getId(),
            it.getCollectionId(),
            dataNode,
            it.getStatus(),
            it.getCreatedAt(),
            it.getUpdatedAt()
        );
    }
}

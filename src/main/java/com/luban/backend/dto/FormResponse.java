package com.luban.backend.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luban.backend.entity.Form;

import java.time.Instant;

public record FormResponse(
        String id,
        String siteId,
        String pageId,
        String name,
        JsonNode fieldSchema,
        JsonNode submitConfig,
        JsonNode dedupKeys,
        int dedupWindow,
        String dedupPolicy,
        JsonNode antiSpam,
        String status,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant createdAt,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant updatedAt
) {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static FormResponse fromEntity(Form f) {
        if (f == null) return null;
        return new FormResponse(
                f.getId(), f.getSiteId(), f.getPageId(), f.getName(),
                parse(f.getFieldSchemaJson()), parse(f.getSubmitConfigJson()),
                parse(f.getDedupKeysJson()), f.getDedupWindow(), f.getDedupPolicy(),
                parse(f.getAntiSpamJson()), f.getStatus(), f.getCreatedAt(), f.getUpdatedAt()
        );
    }

    private static JsonNode parse(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }
}

package com.luban.backend.shared.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luban.backend.shared.entity.PageVersion;

import java.time.Instant;

/**
 * V2-T8 版本响应。includeSchema=false 时省略 schemaJson（列表用），
 * true 时含 schema（详情/回滚用）。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PageVersionResponse(
    String id,
    String pageId,
    int versionNo,
    JsonNode schema,
    String summary,
    String createdBy,
    @JsonFormat(shape = JsonFormat.Shape.STRING) Instant createdAt
) {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static PageVersionResponse fromEntity(PageVersion v, boolean includeSchema) {
        if (v == null) return null;
        JsonNode schemaNode = null;
        if (includeSchema && v.getSchemaJson() != null && !v.getSchemaJson().isEmpty()) {
            try {
                schemaNode = MAPPER.readTree(v.getSchemaJson());
            } catch (Exception ignored) {
                schemaNode = MAPPER.createObjectNode();
            }
        }
        return new PageVersionResponse(
            v.getId(),
            v.getPageId(),
            v.getVersionNo(),
            schemaNode,
            v.getSummary(),
            v.getCreatedBy(),
            v.getCreatedAt()
        );
    }
}

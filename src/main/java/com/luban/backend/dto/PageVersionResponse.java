package com.luban.backend.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luban.backend.entity.PageVersion;

import java.time.Instant;

/**
 * 页面版本响应（schema 为解析后的 JSON 节点，便于前端直接渲染）。
 */
public record PageVersionResponse(
    String id,
    String siteId,
    String pageId,
    int version,
    JsonNode schema,
    String operatorId,
    @JsonFormat(shape = JsonFormat.Shape.STRING) Instant createdAt
) {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static PageVersionResponse fromEntity(PageVersion v) {
        if (v == null) return null;
        JsonNode schemaNode = null;
        if (v.getSchemaJson() != null && !v.getSchemaJson().isEmpty()) {
            try {
                schemaNode = MAPPER.readTree(v.getSchemaJson());
            } catch (Exception ignored) {
                schemaNode = MAPPER.createObjectNode();
            }
        }
        return new PageVersionResponse(
            v.getId(),
            v.getSiteId(),
            v.getPageId(),
            v.getVersion(),
            schemaNode,
            v.getOperatorId(),
            v.getCreatedAt()
        );
    }
}

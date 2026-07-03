package com.luban.backend.shared.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.luban.backend.shared.entity.Template;

import java.time.Instant;

/**
 * Template 响应 DTO（template-marketplace plan）。
 * schema 不含在列表响应里（体积大），需单独 GET /templates/{id}/schema 取。
 */
public record TemplateResponse(
        String id,
        String slug,
        String name,
        String category,
        String description,
        String thumbnail,
        String authorId,
        String status,
        Integer latestVersion,
        Integer installCount,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant createdAt,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant updatedAt
) {
    public static TemplateResponse fromEntity(Template t, int installCount) {
        if (t == null) return null;
        return new TemplateResponse(
                t.getId(), t.getSlug(), t.getName(), t.getCategory(),
                t.getDescription(), t.getThumbnail(), t.getAuthorId(),
                t.getStatus(), t.getLatestVersion(), installCount,
                t.getCreatedAt(), t.getUpdatedAt()
        );
    }
}

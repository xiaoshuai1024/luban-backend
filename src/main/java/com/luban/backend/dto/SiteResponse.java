package com.luban.backend.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.luban.backend.entity.Site;

import java.time.Instant;

public record SiteResponse(
    String id,
    String name,
    String slug,
    String baseUrl,
    String status,
    @JsonFormat(shape = JsonFormat.Shape.STRING) Instant createdAt,
    @JsonFormat(shape = JsonFormat.Shape.STRING) Instant updatedAt
) {
    public static SiteResponse fromEntity(Site s) {
        if (s == null) return null;
        return new SiteResponse(
            s.getId(),
            s.getName(),
            s.getSlug(),
            s.getBaseUrl(),
            s.getStatus(),
            s.getCreatedAt(),
            s.getUpdatedAt()
        );
    }
}

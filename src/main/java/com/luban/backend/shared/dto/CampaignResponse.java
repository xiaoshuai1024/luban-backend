package com.luban.backend.shared.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.luban.backend.shared.entity.Campaign;

import java.time.Instant;

/**
 * Campaign 响应 DTO（app-deeplink-backend-arch plan T13）。
 */
public record CampaignResponse(
        String id,
        String siteId,
        String name,
        String status,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant startAt,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant endAt,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant createdAt,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant updatedAt
) {
    public static CampaignResponse fromEntity(Campaign c) {
        if (c == null) return null;
        return new CampaignResponse(
                c.getId(), c.getSiteId(), c.getName(), c.getStatus(),
                c.getStartAt(), c.getEndAt(), c.getCreatedAt(), c.getUpdatedAt()
        );
    }
}

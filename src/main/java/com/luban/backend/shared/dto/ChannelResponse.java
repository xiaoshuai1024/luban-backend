package com.luban.backend.shared.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luban.backend.shared.entity.Channel;

import java.time.Instant;

/**
 * Channel 响应 DTO（app-deeplink-backend-arch plan T12）。
 */
public record ChannelResponse(
        String id,
        String siteId,
        String campaignId,
        String code,
        String shortUrl,
        String type,
        JsonNode utmTemplate,
        String targetPageId,
        String status,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant createdAt,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant updatedAt
) {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static ChannelResponse fromEntity(Channel ch) {
        if (ch == null) return null;
        return new ChannelResponse(
                ch.getId(), ch.getSiteId(), ch.getCampaignId(), ch.getCode(),
                ch.getShortUrl(), ch.getType(),
                parseJson(ch.getUtmTemplate()),
                ch.getTargetPageId(), ch.getStatus(), ch.getCreatedAt(), ch.getUpdatedAt()
        );
    }

    private static JsonNode parseJson(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return MAPPER.readTree(raw);
        } catch (Exception ignored) {
            return null;
        }
    }
}

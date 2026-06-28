package com.luban.backend.shared.dto;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Channel 创建/更新请求（app-deeplink-backend-arch plan T12）。
 *
 * <p>code 可选（运营指定 or 系统生成，plan 决策 2）。
 * campaignId 可选（channel 可独立存在，plan 决策 4）。
 */
public record ChannelSaveRequest(
        String siteId,
        /** 短码：运营指定 or null（系统生成 base62 6 位） */
        String code,
        String targetPageId,
        /** qrcode / h5 / social / ad / miniapp */
        String type,
        JsonNode utmTemplate,
        /** 可空：不挂活动 */
        String campaignId,
        String status
) {}

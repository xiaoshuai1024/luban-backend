package com.luban.backend.shared.dto;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 短链解析结果（app-deeplink-backend-arch plan T14）。
 *
 * <p>由 ChannelReadService 解析 channel 后返回，供 BFF 302 重定向与 App 直消费。
 */
public record ShortLinkResolveResult(
        /** 目标站点 slug（website/BFF 用它拼 URL） */
        String siteSlug,
        /** 目标页面站内路径 */
        String pagePath,
        /** 渠道短码（用于归因 ?channel=code） */
        String channelCode,
        /** UTM 模板（已解析为 JSON 对象，BFF 拼到 query） */
        JsonNode utmTemplate,
        /** channel ID（可选，调试用） */
        String channelId
) {}

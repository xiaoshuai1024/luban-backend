package com.luban.backend.publicside.controller;

import com.luban.backend.publicside.service.ChannelReadService;
import com.luban.backend.shared.dto.ShortLinkResolveResult;
import org.springframework.web.bind.annotation.*;

/**
 * 短链解析（C 端公开，app-deeplink-backend-arch plan T14）。
 *
 * <p>{@code GET /public/short/:shortCode} 返回 channel 解析结果（siteSlug + pagePath + utm）。
 * 供 BFF 302 重定向（web 场景）与 App 直消费（链路 B，plan 决策 1）。
 *
 * <p>鉴权：AuthFilter {@code /public/*} 白名单放行，无需 X-User-ID。
 */
@RestController
@RequestMapping("/public/short")
public class ShortLinkController {

    private final ChannelReadService channelReadService;

    public ShortLinkController(ChannelReadService channelReadService) {
        this.channelReadService = channelReadService;
    }

    /**
     * 解析短链。
     *
     * @param shortCode 短码（channel.short_url，格式 [a-zA-Z0-9_-]{1,32}）
     * @return { siteSlug, pagePath, channelCode, utmTemplate, channelId }
     * @throws com.luban.backend.shared.exception.BusinessException 404 SHORT_LINK_NOT_FOUND / 410 SHORT_LINK_INACTIVE / 503 TARGET_PAGE_UNAVAILABLE
     */
    @GetMapping("/{shortCode}")
    public ShortLinkResolveResult resolve(@PathVariable String shortCode) {
        return channelReadService.resolve(shortCode);
    }
}

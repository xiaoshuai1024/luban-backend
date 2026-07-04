package com.luban.backend.publicside.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luban.backend.shared.domain.ChannelDomain;
import com.luban.backend.shared.dto.ShortLinkResolveResult;
import com.luban.backend.shared.entity.Channel;
import com.luban.backend.shared.entity.Page;
import com.luban.backend.shared.entity.Site;
import com.luban.backend.shared.exception.BusinessException;
import com.luban.backend.shared.mapper.ChannelMapper;
import com.luban.backend.shared.mapper.PageMapper;
import com.luban.backend.shared.mapper.SiteMapper;
import org.springframework.stereotype.Service;

/**
 * 短链解析读模型（app-deeplink-backend-arch plan T14）。
 *
 * <p>C 端公开服务：按 short_url 查 channel → 取 target page → 取 site slug → 返回解析结果。
 * 不依赖 operatorside（符合 plan §6.1 隔离）。
 *
 * <p>状态语义：
 * <ul>
 *   <li>channel 不存在 → 404 SHORT_LINK_NOT_FOUND</li>
 *   <li>channel.status=inactive → 410 SHORT_LINK_INACTIVE（区分"不存在"与"已停用"）</li>
 *   <li>target page 已 archived → 503 TARGET_PAGE_UNAVAILABLE（降级）</li>
 * </ul>
 */
@Service
public class ChannelReadService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ChannelMapper channelMapper;
    private final PageMapper pageMapper;
    private final SiteMapper siteMapper;

    public ChannelReadService(ChannelMapper channelMapper, PageMapper pageMapper, SiteMapper siteMapper) {
        this.channelMapper = channelMapper;
        this.pageMapper = pageMapper;
        this.siteMapper = siteMapper;
    }

    /**
     * 解析短链。
     *
     * @param shortUrl 短码（channel.short_url）
     * @return 解析结果（siteSlug + pagePath + channelCode + utm）
     */
    public ShortLinkResolveResult resolve(String shortUrl) {
        // 短码格式校验（防注入，对齐敏感字段清单）
        if (shortUrl == null || !shortUrl.matches(ChannelDomain.CODE_PATTERN)) {
            throw BusinessException.shortLinkNotFound();
        }

        Channel channel = channelMapper.getByShortUrl(shortUrl);
        if (channel == null) {
            throw BusinessException.shortLinkNotFound();
        }
        // 区分"不存在"(404) 与"已停用"(410)
        if (!"active".equals(channel.getStatus())) {
            throw BusinessException.shortLinkInactive();
        }

        // 取目标 page（用 channel 记录的 targetPageId + siteId，防开放重定向）
        Page page = pageMapper.getByIdAndSiteId(channel.getTargetPageId(), channel.getSiteId());
        if (page == null) {
            throw BusinessException.shortLinkNotFound();
        }
        // target page 已下线（archived）→ 503
        if ("archived".equals(page.getStatus())) {
            throw new BusinessException(503, "TARGET_PAGE_UNAVAILABLE", "目标页面已下线");
        }

        // 取 site slug（website/BFF 用它拼 URL）
        Site site = siteMapper.getById(page.getSiteId());
        if (site == null) {
            throw BusinessException.shortLinkNotFound();
        }

        JsonNode utmNode = parseJson(channel.getUtmTemplate());
        return new ShortLinkResolveResult(
                site.getSlug(),
                page.getPath(),
                channel.getCode(),
                utmNode,
                channel.getId()
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

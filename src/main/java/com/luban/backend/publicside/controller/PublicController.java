package com.luban.backend.publicside.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luban.backend.shared.dto.CollectionItemResponse;
import com.luban.backend.shared.dto.PageResponse;
import com.luban.backend.shared.dto.SiteConfigDto;
import com.luban.backend.shared.port.PublicCollectionPort;
import com.luban.backend.shared.port.SiteConfigReadPort;
import com.luban.backend.publicside.service.PublicPageService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 公开接口：供对外站点（如 luban-website）按站点 slug + 路径获取已发布页面，无需鉴权。
 * 站点配置经 {@link SiteConfigReadPort}（依赖倒置），不直连 SiteMapper/Site entity。
 */
@RestController
@RequestMapping("/public")
public class PublicController {

    private final PublicPageService publicPageService;
    private final PublicCollectionPort collectionService;
    private final SiteConfigReadPort siteConfigReadPort;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PublicController(PublicPageService publicPageService, PublicCollectionPort collectionService,
                            SiteConfigReadPort siteConfigReadPort) {
        this.publicPageService = publicPageService;
        this.collectionService = collectionService;
        this.siteConfigReadPort = siteConfigReadPort;
    }

    /**
     * 按路径获取已发布页面（仅 status=published）
     * GET /backend/public/sites/:slug/pages?path=/home
     */
    @GetMapping(value = "/sites/{slug}/pages", params = "path")
    public PageResponse getByPath(@PathVariable String slug, @RequestParam String path) {
        return publicPageService.getPublishedPageBySlugAndPath(slug, path);
    }

    /**
     * V2-T10 公开站点配置：返回站点级 analytics 配置（GA4/百度统计/Facebook Pixel）。
     * GET /backend/public/sites/:slug/config
     * website 用此注入第三方分析 SDK 脚本。analytics 为 null 时不输出。
     */
    @GetMapping("/sites/{slug}/config")
    public Map<String, Object> getSiteConfig(@PathVariable String slug) {
        return siteConfigReadPort.findBySlug(slug)
                .map(this::buildConfigResponse)
                .orElseGet(() -> Map.of("analytics", Map.of()));
    }

    private Map<String, Object> buildConfigResponse(SiteConfigDto site) {
        JsonNode analytics = null;
        if (site.analyticsJson() != null && !site.analyticsJson().isEmpty()) {
            try {
                analytics = objectMapper.readTree(site.analyticsJson());
            } catch (Exception ignored) {
            }
        }
        return Map.of(
            "name", site.name() != null ? site.name() : "",
            "baseUrl", site.baseUrl() != null ? site.baseUrl() : "",
            "analytics", analytics != null ? analytics : objectMapper.createObjectNode()
        );
    }

    /**
     * V2-T7 公开 collection items：website SSR 渲染 CMS 绑定时拉取内容项。
     * GET /backend/public/sites/:slug/collections/:collectionId/items
     * 仅返回 active 状态 collection 的 active items（按 updated_at desc）。
     */
    @GetMapping("/sites/{slug}/collections/{collectionId}/items")
    public List<CollectionItemResponse> getPublicCollectionItems(
            @PathVariable String slug,
            @PathVariable String collectionId) {
        return collectionService.listPublicItems(slug, collectionId);
    }
}

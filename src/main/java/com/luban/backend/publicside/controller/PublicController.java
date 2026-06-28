package com.luban.backend.publicside.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luban.backend.shared.dto.CollectionItemResponse;
import com.luban.backend.shared.dto.PageResponse;
import com.luban.backend.shared.entity.Site;
import com.luban.backend.shared.mapper.SiteMapper;
import com.luban.backend.operatorside.service.CollectionService;
import com.luban.backend.publicside.service.PublicPageService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 公开接口：供对外站点（如 luban-website）按站点 slug + 路径获取已发布页面，无需鉴权。
 */
@RestController
@RequestMapping("/public")
public class PublicController {

    private final PublicPageService publicPageService;
    private final CollectionService collectionService;
    private final SiteMapper siteMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PublicController(PublicPageService publicPageService, CollectionService collectionService, SiteMapper siteMapper) {
        this.publicPageService = publicPageService;
        this.collectionService = collectionService;
        this.siteMapper = siteMapper;
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
        Site site = siteMapper.getBySlug(slug);
        if (site == null) {
            return Map.of("analytics", Map.of());
        }
        JsonNode analytics = null;
        if (site.getAnalyticsJson() != null && !site.getAnalyticsJson().isEmpty()) {
            try {
                analytics = objectMapper.readTree(site.getAnalyticsJson());
            } catch (Exception ignored) {
            }
        }
        return Map.of(
            "name", site.getName() != null ? site.getName() : "",
            "baseUrl", site.getBaseUrl() != null ? site.getBaseUrl() : "",
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

package com.luban.backend.controller;

import com.luban.backend.dto.PageResponse;
import com.luban.backend.service.PublicPageService;
import org.springframework.web.bind.annotation.*;

/**
 * 公开接口：供对外站点（如 luban-website）按站点 slug + 路径获取已发布页面，无需鉴权。
 */
@RestController
@RequestMapping("/public/sites/{slug}/pages")
public class PublicController {

    private final PublicPageService publicPageService;

    public PublicController(PublicPageService publicPageService) {
        this.publicPageService = publicPageService;
    }

    /**
     * 按路径获取已发布页面（仅 status=published）
     * GET /backend/public/sites/:slug/pages?path=/home
     */
    @GetMapping(params = "path")
    public PageResponse getByPath(@PathVariable String slug, @RequestParam String path) {
        return publicPageService.getPublishedPageBySlugAndPath(slug, path);
    }
}

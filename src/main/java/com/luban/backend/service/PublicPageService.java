package com.luban.backend.service;

import com.luban.backend.dto.PageResponse;
import com.luban.backend.entity.PublishedPage;
import com.luban.backend.entity.Site;
import com.luban.backend.exception.BusinessException;
import com.luban.backend.mapper.PublishedPageMapper;
import com.luban.backend.mapper.SiteMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

/**
 * 公开接口：按站点 slug + 路径返回已发布页面（无需鉴权）。
 *
 * <p>P0 发布闭环：改读 {@link PublishedPageMapper}（published_pages 发布快照表），
 * 不再直接读 pages 草稿表。这样编辑草稿不影响线上内容。
 */
@Service
public class PublicPageService {

    private final SiteMapper siteMapper;
    private final PublishedPageMapper publishedPageMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PublicPageService(SiteMapper siteMapper, PublishedPageMapper publishedPageMapper) {
        this.siteMapper = siteMapper;
        this.publishedPageMapper = publishedPageMapper;
    }

    public PageResponse getPublishedPageBySlugAndPath(String slug, String path) {
        Site site = siteMapper.getBySlug(slug);
        if (site == null) throw BusinessException.siteNotFound();
        String pathNorm = path != null && !path.isEmpty() ? path : "/";
        if (!pathNorm.startsWith("/")) pathNorm = "/" + pathNorm;
        PublishedPage published = publishedPageMapper.getBySiteIdAndPath(site.getId(), pathNorm);
        if (published == null) throw BusinessException.pageNotFound();
        return toResponse(published);
    }

    private PageResponse toResponse(PublishedPage p) {
        JsonNode schema = null;
        JsonNode seo = null;
        try {
            if (p.getSchemaJson() != null) schema = objectMapper.readTree(p.getSchemaJson());
            if (p.getSeoJson() != null) seo = objectMapper.readTree(p.getSeoJson());
        } catch (Exception e) {
            System.err.println("[WARN] 发布页面 JSON 解析失败 pageId=" + p.getPageId() + ": " + e.getMessage());
        }
        return new PageResponse(
                p.getPageId(), p.getSiteId(), p.getName(), p.getPath(),
                "published", schema, seo, p.getPublishedAt(), p.getPublishedAt()
        );
    }
}

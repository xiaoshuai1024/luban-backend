package com.luban.backend.publicside.service;

import com.luban.backend.shared.dto.PageResponse;
import com.luban.backend.shared.entity.PublishedPage;
import com.luban.backend.shared.entity.Site;
import com.luban.backend.shared.exception.BusinessException;
import com.luban.backend.shared.port.PublishedPageReadPort;
import com.luban.backend.shared.repository.SiteRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 公开接口：按站点 slug + 路径返回已发布页面（无需鉴权）。
 *
 * <p>P0 发布闭环：读 published_pages（发布快照表），不再直接读 pages 草稿表，
 * 这样编辑草稿不影响线上内容。读路径经 {@link PublishedPageReadPort}（依赖倒置），
 * 站点解析经 {@link SiteRepository}，不直连 Mapper。
 */
@Service
public class PublicPageService {

    private static final Logger log = LoggerFactory.getLogger(PublicPageService.class);

    private final SiteRepository siteRepository;
    private final PublishedPageReadPort publishedPageReadPort;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PublicPageService(SiteRepository siteRepository, PublishedPageReadPort publishedPageReadPort) {
        this.siteRepository = siteRepository;
        this.publishedPageReadPort = publishedPageReadPort;
    }

    public PageResponse getPublishedPageBySlugAndPath(String slug, String path) {
        Site site = siteRepository.findBySlug(slug)
                .map(com.luban.backend.shared.domain.SiteAggregate::toSite).orElse(null);
        if (site == null) throw BusinessException.siteNotFound();
        String pathNorm = path != null && !path.isEmpty() ? path : "/";
        if (!pathNorm.startsWith("/")) pathNorm = "/" + pathNorm;
        PublishedPage published = publishedPageReadPort.findBySiteIdAndPath(site.getId(), pathNorm).orElse(null);
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
            // 生产化：SLF4J Logger 替代 System.err（保持"不阻塞发布"行为，但可观测）
            log.warn("发布页面 JSON 解析失败 pageId={}: {}", p.getPageId(), e.getMessage());
        }
        return new PageResponse(
                p.getPageId(), p.getSiteId(), p.getName(), p.getPath(),
                "published", schema, seo, p.getPublishedAt(), p.getPublishedAt()
        );
    }
}

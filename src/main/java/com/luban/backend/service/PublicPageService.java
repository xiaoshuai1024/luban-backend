package com.luban.backend.service;

import com.luban.backend.dto.PageResponse;
import com.luban.backend.entity.Page;
import com.luban.backend.entity.Site;
import com.luban.backend.exception.BusinessException;
import com.luban.backend.mapper.PageMapper;
import com.luban.backend.mapper.SiteMapper;
import org.springframework.stereotype.Service;

/**
 * 公开接口：按站点 slug + 路径返回已发布页面（无需鉴权）
 */
@Service
public class PublicPageService {

    private final SiteMapper siteMapper;
    private final PageMapper pageMapper;

    public PublicPageService(SiteMapper siteMapper, PageMapper pageMapper) {
        this.siteMapper = siteMapper;
        this.pageMapper = pageMapper;
    }

    public PageResponse getPublishedPageBySlugAndPath(String slug, String path) {
        Site site = siteMapper.getBySlug(slug);
        if (site == null) throw BusinessException.siteNotFound();
        String pathNorm = path != null && !path.isEmpty() ? path : "/";
        if (!pathNorm.startsWith("/")) pathNorm = "/" + pathNorm;
        Page page = pageMapper.getBySiteIdAndPathPublished(site.getId(), pathNorm);
        if (page == null) throw BusinessException.pageNotFound();
        return PageResponse.fromEntity(page);
    }
}

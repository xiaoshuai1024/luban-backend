package com.luban.backend.operatorside.repository;

import com.luban.backend.shared.domain.PageAggregate;
import com.luban.backend.shared.entity.Page;
import com.luban.backend.shared.mapper.PageMapper;
import com.luban.backend.shared.repository.PageRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 页面仓储实现（backend-ddd-refactor plan v2 T6）。
 *
 * <p>save 语义：update 不含 published_at/published_by 列（PageMapper.update 刻意隔离草稿/发布审计），
 * 已发布页（publishedAt!=null）save 后补刷 updatePublishStatus 保持审计列持久化。
 */
@Repository
public class PageRepositoryImpl implements PageRepository {

    private final PageMapper pageMapper;

    public PageRepositoryImpl(PageMapper pageMapper) {
        this.pageMapper = pageMapper;
    }

    @Override
    public Optional<PageAggregate> findById(String id, String siteId) {
        Page p = pageMapper.getByIdAndSiteId(id, siteId);
        return p != null ? Optional.of(PageAggregate.reconstitute(p)) : Optional.empty();
    }

    @Override
    public List<Page> listBySiteId(String siteId) {
        return pageMapper.listBySiteId(siteId);
    }

    @Override
    public void save(PageAggregate aggregate) {
        Page entity = aggregate.toPage();
        if (pageMapper.getByIdAndSiteId(entity.getId(), entity.getSiteId()) == null) {
            pageMapper.insert(entity);
        } else {
            pageMapper.update(entity);
            // 已发布页补刷 published_at/published_by（update SQL 不含这两列）
            if (entity.getPublishedAt() != null) {
                pageMapper.updatePublishStatus(entity.getId(), entity.getSiteId(),
                        entity.getStatus(), entity.getPublishedAt(),
                        entity.getPublishedBy(), entity.getUpdatedAt());
            }
        }
    }

    @Override
    public int deleteByIdAndSiteId(String id, String siteId) {
        return pageMapper.deleteByIdAndSiteId(id, siteId);
    }
}

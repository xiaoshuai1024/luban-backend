package com.luban.backend.operatorside.repository;

import com.luban.backend.shared.domain.PageAggregate;
import com.luban.backend.shared.entity.Page;
import com.luban.backend.shared.mapper.PageMapper;
import com.luban.backend.shared.repository.PageRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
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
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public void save(PageAggregate aggregate) {
        Page entity = aggregate.toPage();
        if (pageMapper.getByIdAndSiteId(entity.getId(), entity.getSiteId()) == null) {
            pageMapper.insert(entity);
        } else {
            // G1 修复 Y3：update SQL 已含 published_at/published_by，单条 update 即可，
            // 不再需要双写（旧实现先 update 再 updatePublishStatus 补刷发布列）。
            pageMapper.update(entity);
        }
    }

    @Override
    public int deleteByIdAndSiteId(String id, String siteId) {
        return pageMapper.deleteByIdAndSiteId(id, siteId);
    }

    @Override
    public void updatePublishStatus(String id, String siteId, String status,
                                    Instant publishedAt, String publishedBy, Instant updatedAt) {
        pageMapper.updatePublishStatus(id, siteId, status, publishedAt, publishedBy, updatedAt);
    }
}

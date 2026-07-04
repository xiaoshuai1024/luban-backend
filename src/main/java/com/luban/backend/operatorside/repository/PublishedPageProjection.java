package com.luban.backend.operatorside.repository;

import com.luban.backend.shared.domain.PageAggregate;
import com.luban.backend.shared.entity.PublishedPage;
import com.luban.backend.shared.exception.BusinessException;
import com.luban.backend.shared.mapper.PublishedPageMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * published_pages 物化视图投影协作者（backend-ddd-refactor plan v2 T6 G1 修复）。
 *
 * <p><b>为什么独立于 {@code PageRepositoryImpl}</b>：{@code published_pages} 是 {@code pages}
 * 聚合的<b>读侧投影/物化视图</b>（C 端公开页面访问），不是另一个聚合。其写入必须与
 * {@code pages} 在<b>同一事务</b>内完成（双写一致性：PUT published ≡ POST /publish），
 * 因此不能用 AFTER_COMMIT 事件 handler（会引入最终一致风险）。
 *
 * <p>把 {@link PublishedPageMapper} 的调用从 {@code PageService} 抽到本协作者，
 * 让 PageService 不再直接依赖 Mapper（ArchUnit 守护 Service↛Mapper），同时保留事务性双写。
 * PageRepository 仍只封装 Page 聚合自身，不混入投影职责。
 */
@Component
public class PublishedPageProjection {

    private final PublishedPageMapper publishedPageMapper;

    public PublishedPageProjection(PublishedPageMapper publishedPageMapper) {
        this.publishedPageMapper = publishedPageMapper;
    }

    /** 投影：发布/重新发布时写入快照（先删旧快照避免 UNIQUE(site_id,path) 冲突）。 */
    public void upsertOnPublish(PageAggregate agg) {
        publishedPageMapper.deleteByPageId(agg.toPage().getId());
        try {
            publishedPageMapper.insert(agg.buildPublishedSnapshot());
        } catch (DataIntegrityViolationException e) {
            if (isDuplicate(e)) throw BusinessException.pagePathConflict();
            throw e;
        }
    }

    /** 投影：下线/归档时按 pageId+siteId 清理快照。 */
    public void removeByPageAndSite(String pageId, String siteId) {
        publishedPageMapper.deleteByPageIdAndSiteId(pageId, siteId);
    }

    /** 投影：状态由非 published→published 时写快照；反向则清。 */
    public void syncOnStatusChange(String siteId, PageAggregate agg, String oldStatus) {
        String newStatus = agg.toPage().getStatus();
        boolean nowPublished = "published".equals(newStatus);
        boolean wasPublished = "published".equals(oldStatus);
        String pageId = agg.toPage().getId();
        if (nowPublished && !wasPublished) {
            upsertOnPublish(agg);
        } else if (!nowPublished && wasPublished) {
            removeByPageAndSite(pageId, siteId);
        }
    }

    private static boolean isDuplicate(DataIntegrityViolationException e) {
        if (e == null || e.getMessage() == null) return false;
        String m = e.getMessage();
        return m.contains("Duplicate") || m.contains("Unique index") || m.contains("primary key violation");
    }
}

package com.luban.backend.shared.repository;

import com.luban.backend.shared.domain.PageAggregate;
import com.luban.backend.shared.entity.Page;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 页面仓储接口（backend-ddd-refactor plan v2 T6）。
 * 封装 PageMapper。published_pages 快照表（双写一致性目标表）由 PageService 直接维护。
 */
public interface PageRepository {
    Optional<PageAggregate> findById(String id, String siteId);
    List<Page> listBySiteId(String siteId);
    void save(PageAggregate aggregate);
    int deleteByIdAndSiteId(String id, String siteId);

    /**
     * 单独更新发布状态（不改草稿内容）。
     *
     * <p>用于发布/下线场景：草稿内容（schema/seo/name/path）保留不变，
     * 仅刷 status / published_at / published_by / updated_at 审计列。
     * 对齐 {@link com.luban.backend.shared.mapper.PageMapper#updatePublishStatus}。
     */
    void updatePublishStatus(String id, String siteId, String status,
                             Instant publishedAt, String publishedBy, Instant updatedAt);
}


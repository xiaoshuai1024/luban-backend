package com.luban.backend.shared.repository;

import com.luban.backend.shared.domain.PageAggregate;
import com.luban.backend.shared.entity.Page;

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
}

package com.luban.backend.shared.repository;

import com.luban.backend.shared.domain.SiteAggregate;
import com.luban.backend.shared.entity.Site;

import java.util.List;
import java.util.Optional;

/**
 * 站点仓储接口（backend-ddd-refactor plan v2 T5）。
 *
 * <p>领域抽象，封装 {@code SiteMapper}（Site 聚合读写）。
 * 7 表级联删除由 SiteService 编排（子表属其它聚合），本 Repository 仅封装 Site 自身。
 */
public interface SiteRepository {
    Optional<SiteAggregate> findById(String id);
    Optional<SiteAggregate> findBySlug(String slug);
    List<Site> list();
    void save(SiteAggregate aggregate);
    void deleteById(String id);
}

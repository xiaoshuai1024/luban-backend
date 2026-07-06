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

    /**
     * 存在性校验（轻量 SELECT 1，避免全字段加载）。
     * 替代 Service 层对 {@code SiteMapper.getById(...) != null} 的白名单直连——
     * 关闭 seed/readonly 白名单后，所有「站点是否存在」校验统一经此入口。
     */
    boolean existsById(String id);

    boolean existsBySlug(String slug);
}

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

    /**
     * 按 (id, siteId) 加载页面 entity（读模型，非聚合根）。
     * 供 ChannelService（page 归属校验）、PageVersionService（回滚读 schema）使用——
     * 这些场景需原始 entity 字段且无需聚合根不变量校验。不存在返回 null。
     */
    Page findEntityByIdAndSiteId(String id, String siteId);

    List<Page> listBySiteId(String siteId);
    void save(PageAggregate aggregate);
    int deleteByIdAndSiteId(String id, String siteId);

    /**
     * 直接更新页面 entity（非聚合根路径）。
     * 供 PageVersionService.rollback 使用——回滚需覆写 schema_json 且语义上等价于一次"保存"，
     * 但回滚源是版本快照而非聚合根方法，故提供 entity 级写入入口避免绕过聚合根的假象。
     */
    void updateEntity(Page page);

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


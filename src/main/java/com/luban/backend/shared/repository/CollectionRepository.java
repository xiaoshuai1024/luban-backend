package com.luban.backend.shared.repository;

import com.luban.backend.shared.domain.CollectionAggregate;
import com.luban.backend.shared.entity.ContentCollection;
import com.luban.backend.shared.entity.ContentCollectionItem;

import java.util.List;

/**
 * 内容集合仓储接口（backend-ddd-refactor plan v2 T12）。
 *
 * <p>领域抽象。封装 {@code CollectionMapper}（Collection 根 + Item 聚合内实体）。
 * 聚合根加载时一并加载 items（聚合内实体），save 时根据聚合根 pendingChanges 持久化 item 变更。
 *
 * @see CollectionAggregate
 */
public interface CollectionRepository {

    /**
     * 按 (id, siteId) 加载聚合根（含聚合内 items），不存在返回 null。
     */
    CollectionAggregate findByIdWithItems(String id, String siteId);

    /** 仅加载根（无 items，用于存在性校验/get 读模型）。 */
    ContentCollection findById(String id, String siteId);

    /** 列表查询（读模型，按 siteId）。 */
    List<ContentCollection> listBySiteId(String siteId);

    /** 保存聚合根（insert or update 根，并执行 pending item 变更）。 */
    void save(CollectionAggregate aggregate);

    /** 按 (id, siteId) 删除根（items 由 FK CASCADE 自动清）。返回影响行数。 */
    int delete(String id, String siteId);

    // === Item 读模型（聚合内实体的查询，用于 getItem / public read） ===

    /** 列出 collection 下所有 items（读模型）。 */
    List<ContentCollectionItem> listItems(String collectionId);

    /** 按 (itemId, collectionId) 查 item（读模型，不存在返回 null）。 */
    ContentCollectionItem findItem(String itemId, String collectionId);
}

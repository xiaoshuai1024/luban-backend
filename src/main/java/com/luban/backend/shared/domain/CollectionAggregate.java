package com.luban.backend.shared.domain;

import com.luban.backend.shared.domain.event.DomainEvent;
import com.luban.backend.shared.entity.ContentCollection;
import com.luban.backend.shared.entity.ContentCollectionItem;
import com.luban.backend.shared.exception.BusinessException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 内容集合聚合根（backend-ddd-refactor plan v2 T12）。
 *
 * <p>封装 Collection 域不变量（对齐 {@code .agents/rules/luban-engineering-principles.md}）：
 * <ul>
 *   <li><b>聚合内实体</b>：ContentCollection（根）+ ContentCollectionItem（聚合内实体）</li>
 *   <li><b>item 归属结构性保证</b>：{@link #newItem} 生成的 item 的 collectionId 永远等于聚合根 id，
 *       外部无法指定其它 collection——DDD 聚合边界由聚合根守护</li>
 *   <li><b>跨聚合误操作防护</b>：{@link #updateItem}/{@link #removeItem} 操作非本聚合 item 时抛
 *       COLLECTION_ITEM_NOT_FOUND（防 Service 传错 item）</li>
 *   <li><b>status 白名单</b>：active/disabled</li>
 * </ul>
 *
 * <p>item 变更（insert/update/delete）通过 {@link #pendingItemChanges} 暴露给 Repository，
 * Repository 据此调用对应 Mapper 方法（避免 diff 整个 items 列表）。
 *
 * @see ContentCollection
 * @see ContentCollectionItem
 */
public final class CollectionAggregate {

    /** 允许的状态白名单。 */
    public static final Set<String> ALLOWED_STATUSES = Set.of("active", "disabled");
    public static final String STATUS_ACTIVE = "active";

    private final ContentCollection root;
    /** 聚合内 items，按 id 索引（保留插入顺序）。 */
    private final Map<String, ContentCollectionItem> items = new LinkedHashMap<>();
    /** 待持久化的 item 变更（Repository.save 后由 Service 拉取执行）。 */
    private final List<NewItemCommand> pendingInserts = new ArrayList<>();
    private final List<ContentCollectionItem> pendingUpdates = new ArrayList<>();
    private final List<String> pendingItemDeletes = new ArrayList<>();
    private final List<DomainEvent> events = new ArrayList<>();

    private CollectionAggregate(ContentCollection root) {
        this.root = root;
    }

    /**
     * 工厂：创建新集合（默认 status=active）。
     */
    public static CollectionAggregate newCollection(String id, String siteId, String name,
                                                    String fieldSchemaJson, String status) {
        String resolvedStatus = status != null ? status : STATUS_ACTIVE;
        validateStatus(resolvedStatus);
        Instant now = Instant.now();
        ContentCollection c = new ContentCollection();
        c.setId(id);
        c.setSiteId(siteId);
        c.setName(name);
        c.setFieldSchemaJson(fieldSchemaJson);
        c.setStatus(resolvedStatus);
        c.setCreatedAt(now);
        c.setUpdatedAt(now);
        return new CollectionAggregate(c);
    }

    /**
     * 工厂：从持久化重建（含聚合内 items）。
     */
    public static CollectionAggregate reconstitute(ContentCollection persisted, List<ContentCollectionItem> items) {
        CollectionAggregate agg = new CollectionAggregate(persisted);
        if (items != null) {
            for (ContentCollectionItem item : items) {
                agg.items.put(item.getId(), item);
            }
        }
        return agg;
    }

    /** Patch 更新集合根字段（null 保留原值）。 */
    public void update(String name, String fieldSchemaJson, String status) {
        if (status != null) {
            validateStatus(status);
            root.setStatus(status);
        }
        if (name != null) root.setName(name);
        if (fieldSchemaJson != null) root.setFieldSchemaJson(fieldSchemaJson);
        root.setUpdatedAt(Instant.now());
    }

    /**
     * 创建新 item（聚合根结构性保证 collectionId = 本聚合 id）。
     * 返回命令对象，含待持久化的 item 实体；Repository.save 时由 Service 拉取 pendingInserts。
     */
    public NewItemCommand newItem(String dataJson, String status) {
        ContentCollectionItem item = new ContentCollectionItem();
        item.setId(UUID.randomUUID().toString());
        item.setCollectionId(root.getId());   // 归属保证：永远等于聚合根 id
        item.setDataJson(dataJson);
        item.setStatus(status != null ? status : STATUS_ACTIVE);
        Instant now = Instant.now();
        item.setCreatedAt(now);
        item.setUpdatedAt(now);
        items.put(item.getId(), item);
        NewItemCommand cmd = new NewItemCommand(item);
        pendingInserts.add(cmd);
        return cmd;
    }

    /** 更新本聚合内的 item（item 不属于本聚合抛异常）。 */
    public void updateItem(String itemId, String dataJson, String status) {
        ContentCollectionItem item = items.get(itemId);
        if (item == null) {
            throw BusinessException.collectionItemNotFound();
        }
        if (dataJson != null) item.setDataJson(dataJson);
        if (status != null) {
            validateStatus(status);
            item.setStatus(status);
        }
        item.setUpdatedAt(Instant.now());
        pendingUpdates.add(item);
    }

    /** 移除本聚合内的 item（item 不属于本聚合抛异常）。 */
    public void removeItem(String itemId) {
        if (!items.containsKey(itemId)) {
            throw BusinessException.collectionItemNotFound();
        }
        items.remove(itemId);
        pendingItemDeletes.add(itemId);
    }

    public ContentCollection toCollection() {
        return root;
    }

    /** 聚合内 items 视图（不可变，外部不应直接修改）。 */
    public List<ContentCollectionItem> items() {
        return List.copyOf(items.values());
    }

    public List<NewItemCommand> pendingInserts() { return List.copyOf(pendingInserts); }
    public List<ContentCollectionItem> pendingUpdates() { return List.copyOf(pendingUpdates); }
    public List<String> pendingItemDeletes() { return List.copyOf(pendingItemDeletes); }

    /** 清空待持久化变更（Repository.save 成功后调用）。 */
    public void clearPendingItemChanges() {
        pendingInserts.clear();
        pendingUpdates.clear();
        pendingItemDeletes.clear();
    }

    public List<DomainEvent> pullEvents() {
        List<DomainEvent> drained = new ArrayList<>(events);
        events.clear();
        return drained;
    }

    private static void validateStatus(String status) {
        if (!ALLOWED_STATUSES.contains(status)) {
            throw BusinessException.invalidArgument("status must be one of " + ALLOWED_STATUSES);
        }
    }

    /**
     * 新建 item 命令（含 item 实体 + 是否待 insert 标记）。
     * Repository 据此调用 insertItem。
     */
    public record NewItemCommand(ContentCollectionItem item) {
        public boolean isPendingInsert() { return true; }
    }
}

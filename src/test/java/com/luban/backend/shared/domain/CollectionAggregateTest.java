package com.luban.backend.shared.domain;

import com.luban.backend.shared.entity.ContentCollection;
import com.luban.backend.shared.entity.ContentCollectionItem;
import com.luban.backend.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CollectionAggregate 单测（backend-ddd-refactor plan v2 T12）。
 *
 * <p>锁定真聚合根范式不变量：
 * <ul>
 *   <li>聚合根持有 collection + items（聚合内实体），item 归属由聚合根结构性保证</li>
 *   <li>addItem：新 item 的 collectionId 自动绑定为聚合根 id（不可由外部指定其它 collection）</li>
 *   <li>updateItem/removeItem：item 不属于本聚合时抛异常（防跨聚合误操作）</li>
 *   <li>status 白名单：active/disabled</li>
 * </ul>
 */
class CollectionAggregateTest {

    @Test
    void newCollectionAppliesDefaults() {
        Instant before = Instant.now();
        CollectionAggregate agg = CollectionAggregate.newCollection(
                "c-1", "site-1", "商品列表", "{}", null);

        ContentCollection c = agg.toCollection();
        assertThat(c.getId()).isEqualTo("c-1");
        assertThat(c.getSiteId()).isEqualTo("site-1");
        assertThat(c.getName()).isEqualTo("商品列表");
        assertThat(c.getStatus()).isEqualTo("active");   // 默认
        assertThat(c.getCreatedAt()).isBetween(before, Instant.now().plusSeconds(1));
        assertThat(c.getUpdatedAt()).isEqualTo(c.getCreatedAt());
        assertThat(agg.items()).isEmpty();
    }

    @Test
    void newCollectionRejectsInvalidStatus() {
        assertThatThrownBy(() -> CollectionAggregate.newCollection(
                "c-1", "site-1", "n", "{}", "frozen"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("status");
    }

    @Test
    void reconstitutePreservesPersistedStateWithItems() {
        Instant created = Instant.parse("2026-01-01T00:00:00Z");
        ContentCollection persisted = new ContentCollection();
        persisted.setId("c-9");
        persisted.setSiteId("site-1");
        persisted.setName("旧集合");
        persisted.setStatus("disabled");
        persisted.setCreatedAt(created);
        persisted.setUpdatedAt(created);
        ContentCollectionItem item = new ContentCollectionItem();
        item.setId("i-1");
        item.setCollectionId("c-9");
        item.setDataJson("{\"k\":\"v\"}");
        item.setStatus("active");

        CollectionAggregate agg = CollectionAggregate.reconstitute(persisted, List.of(item));

        assertThat(agg.toCollection().getName()).isEqualTo("旧集合");
        assertThat(agg.items()).hasSize(1);
        assertThat(agg.pullEvents()).isEmpty();
    }

    @Test
    void updatePatchesCollectionFields() {
        CollectionAggregate agg = CollectionAggregate.newCollection(
                "c-1", "site-1", "原名", "{}", "active");

        agg.update("新名", "{\"fields\":[]}", "disabled");

        ContentCollection c = agg.toCollection();
        assertThat(c.getName()).isEqualTo("新名");
        assertThat(c.getFieldSchemaJson()).isEqualTo("{\"fields\":[]}");
        assertThat(c.getStatus()).isEqualTo("disabled");
    }

    @Test
    void updateRejectsInvalidStatus() {
        CollectionAggregate agg = CollectionAggregate.newCollection(
                "c-1", "site-1", "n", "{}", null);

        assertThatThrownBy(() -> agg.update("n", null, "ghost"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void addItemBindsItemToThisCollection() {
        // 关键不变量：addItem 生成的 item，collectionId 永远等于聚合根 id
        // 外部无法指定其它 collectionId，结构性保证 item 归属
        CollectionAggregate agg = CollectionAggregate.newCollection(
                "c-1", "site-1", "列表", "{}", null);

        CollectionAggregate.NewItemCommand cmd = agg.newItem("{\"name\":\"商品A\"}", "active");
        ContentCollectionItem newItem = cmd.item();

        assertThat(newItem.getCollectionId()).isEqualTo("c-1");   // 归属保证
        assertThat(newItem.getDataJson()).isEqualTo("{\"name\":\"商品A\"}");
        assertThat(newItem.getStatus()).isEqualTo("active");
        assertThat(newItem.getId()).isNotBlank();
        assertThat(cmd.isPendingInsert()).isTrue();
    }

    @Test
    void addItemDefaultsStatusToActive() {
        CollectionAggregate agg = CollectionAggregate.newCollection(
                "c-1", "site-1", "列表", "{}", null);

        CollectionAggregate.NewItemCommand cmd = agg.newItem("{}", null);

        assertThat(cmd.item().getStatus()).isEqualTo("active");
    }

    @Test
    void updateItemThrowsWhenItemNotInThisAggregate() {
        // item 不属于本聚合 → 抛异常（防跨聚合误操作）
        CollectionAggregate agg = CollectionAggregate.newCollection(
                "c-1", "site-1", "列表", "{}", null);

        assertThatThrownBy(() -> agg.updateItem("i-foreign", "{\"x\":1}", "active"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo("COLLECTION_ITEM_NOT_FOUND");
    }

    @Test
    void updateItemSucceedsForOwnedItem() {
        ContentCollectionItem existing = new ContentCollectionItem();
        existing.setId("i-1");
        existing.setCollectionId("c-1");
        existing.setDataJson("{\"old\":1}");
        existing.setStatus("active");
        CollectionAggregate agg = CollectionAggregate.reconstitute(
                collectionWithId("c-1"), List.of(existing));

        agg.updateItem("i-1", "{\"new\":2}", "disabled");

        ContentCollectionItem updated = agg.items().stream()
                .filter(i -> "i-1".equals(i.getId())).findFirst().orElseThrow();
        assertThat(updated.getDataJson()).isEqualTo("{\"new\":2}");
        assertThat(updated.getStatus()).isEqualTo("disabled");
    }

    @Test
    void removeItemThrowsWhenItemNotInThisAggregate() {
        CollectionAggregate agg = CollectionAggregate.newCollection(
                "c-1", "site-1", "列表", "{}", null);

        assertThatThrownBy(() -> agg.removeItem("i-foreign"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void removeItemSucceedsForOwnedItem() {
        ContentCollectionItem existing = new ContentCollectionItem();
        existing.setId("i-1");
        existing.setCollectionId("c-1");
        existing.setDataJson("{}");
        existing.setStatus("active");
        CollectionAggregate agg = CollectionAggregate.reconstitute(
                collectionWithId("c-1"), List.of(existing));
        assertThat(agg.items()).hasSize(1);

        agg.removeItem("i-1");

        assertThat(agg.items()).isEmpty();
    }

    private static ContentCollection collectionWithId(String id) {
        ContentCollection c = new ContentCollection();
        c.setId(id);
        c.setSiteId("site-1");
        c.setName("集合");
        c.setStatus("active");
        return c;
    }
}

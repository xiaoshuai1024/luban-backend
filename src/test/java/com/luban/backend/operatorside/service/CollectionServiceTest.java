package com.luban.backend.operatorside.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luban.backend.shared.domain.CollectionAggregate;
import com.luban.backend.shared.dto.CollectionItemResponse;
import com.luban.backend.shared.dto.CollectionResponse;
import com.luban.backend.shared.entity.ContentCollection;
import com.luban.backend.shared.entity.ContentCollectionItem;
import com.luban.backend.shared.entity.Site;
import com.luban.backend.shared.exception.BusinessException;
import com.luban.backend.shared.mapper.SiteMapper;
import com.luban.backend.shared.repository.CollectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CollectionService 单测（backend-ddd-refactor plan v2 T18，补覆盖率）。
 * 覆盖 collection + item CRUD（item 经聚合根保证归属）。
 */
@ExtendWith(MockitoExtension.class)
class CollectionServiceTest {

    @Mock private CollectionRepository collectionRepository;
    @Mock private SiteMapper siteMapper;

    private CollectionService service;
    private static final ObjectMapper JSON = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new CollectionService(collectionRepository, siteMapper);
    }

    private JsonNode node(String json) {
        try { return JSON.readTree(json); } catch (Exception e) { throw new RuntimeException(e); }
    }

    @Test
    void list_returns_collections_when_site_exists() {
        when(siteMapper.getById("site-1")).thenReturn(new Site());
        ContentCollection c = new ContentCollection();
        c.setId("c-1");
        c.setName("商品");
        when(collectionRepository.listBySiteId("site-1")).thenReturn(List.of(c));

        List<CollectionResponse> out = service.list("site-1");

        assertThat(out).hasSize(1);
        assertThat(out.get(0).name()).isEqualTo("商品");
    }

    @Test
    void list_throws_site_not_found() {
        when(siteMapper.getById("site-x")).thenReturn(null);

        assertThatThrownBy(() -> service.list("site-x"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("SITE_NOT_FOUND");
    }

    @Test
    void get_returns_collection() {
        ContentCollection c = new ContentCollection();
        c.setId("c-1");
        c.setName("商品");
        when(collectionRepository.findById("c-1", "site-1")).thenReturn(c);

        CollectionResponse resp = service.get("site-1", "c-1");

        assertThat(resp.name()).isEqualTo("商品");
    }

    @Test
    void get_throws_collection_not_found() {
        when(collectionRepository.findById("c-x", "site-1")).thenReturn(null);

        assertThatThrownBy(() -> service.get("site-1", "c-x"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("COLLECTION_NOT_FOUND");
    }

    @Test
    void create_inserts_when_site_exists() {
        when(siteMapper.getById("site-1")).thenReturn(new Site());

        CollectionResponse resp = service.create("site-1", "新集合", node("{}"), null);

        assertThat(resp.name()).isEqualTo("新集合");
        verify(collectionRepository).save(any());
    }

    @Test
    void create_throws_when_site_not_found() {
        when(siteMapper.getById("site-x")).thenReturn(null);

        assertThatThrownBy(() -> service.create("site-x", "n", node("{}"), null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("SITE_NOT_FOUND");
    }

    @Test
    void update_modifies_collection() {
        ContentCollection c = new ContentCollection();
        c.setId("c-1");
        c.setSiteId("site-1");
        c.setName("旧名");
        c.setStatus("active");
        when(collectionRepository.findByIdWithItems("c-1", "site-1"))
                .thenReturn(CollectionAggregate.reconstitute(c, List.of()));

        CollectionResponse resp = service.update("site-1", "c-1", "新名", node("{\"f\":[]}"), "disabled");

        assertThat(resp.name()).isEqualTo("新名");
        verify(collectionRepository).save(any());
    }

    @Test
    void update_throws_when_not_found() {
        when(collectionRepository.findByIdWithItems("c-x", "site-1")).thenReturn(null);

        assertThatThrownBy(() -> service.update("site-1", "c-x", "n", null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("COLLECTION_NOT_FOUND");
    }

    @Test
    void delete_succeeds() {
        when(collectionRepository.delete("c-1", "site-1")).thenReturn(1);

        service.delete("site-1", "c-1");

        verify(collectionRepository).delete("c-1", "site-1");
    }

    @Test
    void delete_throws_when_not_found() {
        when(collectionRepository.delete("c-x", "site-1")).thenReturn(0);

        assertThatThrownBy(() -> service.delete("site-1", "c-x"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("COLLECTION_NOT_FOUND");
    }

    @Test
    void listItems_returns_items() {
        ContentCollection c = new ContentCollection();
        c.setId("c-1");
        when(collectionRepository.findById("c-1", "site-1")).thenReturn(c);
        ContentCollectionItem item = new ContentCollectionItem();
        item.setId("i-1");
        item.setDataJson("{\"k\":\"v\"}");
        when(collectionRepository.listItems("c-1")).thenReturn(List.of(item));

        List<CollectionItemResponse> out = service.listItems("site-1", "c-1");

        assertThat(out).hasSize(1);
    }

    @Test
    void listItems_throws_when_collection_not_found() {
        when(collectionRepository.findById("c-x", "site-1")).thenReturn(null);

        assertThatThrownBy(() -> service.listItems("site-1", "c-x"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("COLLECTION_NOT_FOUND");
    }

    @Test
    void createItem_creates_via_aggregate() {
        ContentCollection c = new ContentCollection();
        c.setId("c-1");
        when(collectionRepository.findByIdWithItems("c-1", "site-1"))
                .thenReturn(CollectionAggregate.reconstitute(c, List.of()));

        CollectionItemResponse resp = service.createItem("site-1", "c-1", node("{\"name\":\"A\"}"), null);

        assertThat(resp).isNotNull();
        // G1 加强：断言聚合根 pendingInserts 含新 item，且 collectionId 强制 = 聚合根 id（归属不变量）
        ArgumentCaptor<CollectionAggregate> captor = ArgumentCaptor.forClass(CollectionAggregate.class);
        verify(collectionRepository).save(captor.capture());
        assertThat(captor.getValue().pendingInserts()).hasSize(1);
        ContentCollectionItem saved = captor.getValue().pendingInserts().get(0).item();
        assertThat(saved.getCollectionId()).isEqualTo("c-1");   // 归属保证
        assertThat(saved.getDataJson()).isEqualTo("{\"name\":\"A\"}");
        assertThat(saved.getStatus()).isEqualTo("active");   // null status 兜底
    }

    @Test
    void createItem_throws_when_collection_not_found() {
        when(collectionRepository.findByIdWithItems("c-x", "site-1")).thenReturn(null);

        assertThatThrownBy(() -> service.createItem("site-1", "c-x", node("{}"), null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("COLLECTION_NOT_FOUND");
        verify(collectionRepository, never()).save(any());
    }

    @Test
    void updateItem_modifies_owned_item() {
        ContentCollection c = new ContentCollection();
        c.setId("c-1");
        ContentCollectionItem item = new ContentCollectionItem();
        item.setId("i-1");
        item.setCollectionId("c-1");
        item.setDataJson("{}");
        item.setStatus("active");
        when(collectionRepository.findByIdWithItems("c-1", "site-1"))
                .thenReturn(CollectionAggregate.reconstitute(c, List.of(item)));
        ContentCollectionItem updated = new ContentCollectionItem();
        updated.setId("i-1");
        updated.setDataJson("{\"new\":1}");
        when(collectionRepository.findItem("i-1", "c-1")).thenReturn(updated);

        CollectionItemResponse resp = service.updateItem("site-1", "c-1", "i-1", node("{\"new\":1}"), "disabled");

        assertThat(resp).isNotNull();
        // G1 加强：断言聚合根 pendingUpdates 含修改后的 item（dataJson/status）
        ArgumentCaptor<CollectionAggregate> captor = ArgumentCaptor.forClass(CollectionAggregate.class);
        verify(collectionRepository).save(captor.capture());
        assertThat(captor.getValue().pendingUpdates()).hasSize(1);
        ContentCollectionItem pendingUpd = captor.getValue().pendingUpdates().get(0);
        assertThat(pendingUpd.getId()).isEqualTo("i-1");
        assertThat(pendingUpd.getDataJson()).isEqualTo("{\"new\":1}");
        assertThat(pendingUpd.getStatus()).isEqualTo("disabled");
    }

    @Test
    void deleteItem_removes_owned_item() {
        ContentCollection c = new ContentCollection();
        c.setId("c-1");
        ContentCollectionItem item = new ContentCollectionItem();
        item.setId("i-1");
        item.setCollectionId("c-1");
        when(collectionRepository.findByIdWithItems("c-1", "site-1"))
                .thenReturn(CollectionAggregate.reconstitute(c, List.of(item)));

        service.deleteItem("site-1", "c-1", "i-1");

        verify(collectionRepository).save(any());
    }
}

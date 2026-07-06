package com.luban.backend.operatorside.service;
import com.luban.backend.shared.util.JsonUtil;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luban.backend.shared.domain.CollectionAggregate;
import com.luban.backend.shared.dto.CollectionItemResponse;
import com.luban.backend.shared.dto.CollectionResponse;
import com.luban.backend.shared.entity.ContentCollection;
import com.luban.backend.shared.entity.ContentCollectionItem;
import com.luban.backend.shared.exception.BusinessException;
import com.luban.backend.shared.port.PublicCollectionPort;
import com.luban.backend.shared.repository.CollectionRepository;
import com.luban.backend.shared.repository.SiteRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 内容集合应用服务（backend-ddd-refactor plan v2 T12）。
 *
 * <p>用例编排：加载聚合根 → 调聚合根方法 → 保存。
 * 业务不变量（item 归属结构性保证、status 白名单）下沉到 {@link CollectionAggregate}。
 *
 * <p>持久化经 {@link CollectionRepository}（不直接依赖 CollectionMapper，ArchUnit 守护）。
 * {@code listPublicItems}（PublicCollectionPort）是读模型，保留在本服务（plan §3.4）。
 * name UNIQUE 冲突翻译保留在 Service（嗅探 DataIntegrityViolationException）。
 */
@Service
public class CollectionService implements PublicCollectionPort {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CollectionService.class);

    private final CollectionRepository collectionRepository;
    private final SiteRepository siteRepository;


    public CollectionService(CollectionRepository collectionRepository, SiteRepository siteRepository) {
        this.collectionRepository = collectionRepository;
        this.siteRepository = siteRepository;
    }

    // === Collection ===

    public List<CollectionResponse> list(String siteId) {
        if (!siteRepository.existsById(siteId)) throw BusinessException.siteNotFound();
        return collectionRepository.listBySiteId(siteId).stream()
                .map(CollectionResponse::fromEntity).collect(Collectors.toList());
    }

    public CollectionResponse get(String siteId, String id) {
        ContentCollection c = collectionRepository.findById(id, siteId);
        if (c == null) throw BusinessException.collectionNotFound();
        return CollectionResponse.fromEntity(c);
    }

    public CollectionResponse create(String siteId, String name, JsonNode fieldSchema, String status) {
        if (!siteRepository.existsById(siteId)) throw BusinessException.siteNotFound();
        CollectionAggregate agg = CollectionAggregate.newCollection(
                UUID.randomUUID().toString(), siteId, name, jsonToString(fieldSchema), status);
        try {
            collectionRepository.save(agg);
        } catch (DataIntegrityViolationException e) {
            if (isDuplicate(e)) throw BusinessException.collectionNameConflict();
            throw e;
        }
        return CollectionResponse.fromEntity(agg.toCollection());
    }

    public CollectionResponse update(String siteId, String id, String name, JsonNode fieldSchema, String status) {
        CollectionAggregate agg = collectionRepository.findByIdWithItems(id, siteId);
        if (agg == null) throw BusinessException.collectionNotFound();
        agg.update(name,
                fieldSchema != null ? jsonToString(fieldSchema) : null,
                status);
        try {
            collectionRepository.save(agg);
        } catch (DataIntegrityViolationException e) {
            if (isDuplicate(e)) throw BusinessException.collectionNameConflict();
            throw e;
        }
        return CollectionResponse.fromEntity(agg.toCollection());
    }

    public void delete(String siteId, String id) {
        int n = collectionRepository.delete(id, siteId);
        if (n == 0) throw BusinessException.collectionNotFound();
        // collection_items 由 FK ON DELETE CASCADE 自动清理
    }

    // === CollectionItem（经聚合根，结构性保证 item 归属） ===

    public List<CollectionItemResponse> listItems(String siteId, String collectionId) {
        if (collectionRepository.findById(collectionId, siteId) == null) {
            throw BusinessException.collectionNotFound();
        }
        return collectionRepository.listItems(collectionId).stream()
                .map(CollectionItemResponse::fromEntity).collect(Collectors.toList());
    }

    public CollectionItemResponse getItem(String siteId, String collectionId, String itemId) {
        if (collectionRepository.findById(collectionId, siteId) == null) {
            throw BusinessException.collectionNotFound();
        }
        ContentCollectionItem it = collectionRepository.findItem(itemId, collectionId);
        if (it == null) throw BusinessException.collectionItemNotFound();
        return CollectionItemResponse.fromEntity(it);
    }

    public CollectionItemResponse createItem(String siteId, String collectionId, JsonNode data, String status) {
        // 加载聚合根（租户守卫 + 含 items），调聚合根 newItem 保证归属
        CollectionAggregate agg = collectionRepository.findByIdWithItems(collectionId, siteId);
        if (agg == null) throw BusinessException.collectionNotFound();
        CollectionAggregate.NewItemCommand cmd = agg.newItem(jsonToString(data), status);
        collectionRepository.save(agg);   // 持久化 pending insert
        return CollectionItemResponse.fromEntity(cmd.item());
    }

    public CollectionItemResponse updateItem(String siteId, String collectionId, String itemId, JsonNode data, String status) {
        CollectionAggregate agg = collectionRepository.findByIdWithItems(collectionId, siteId);
        if (agg == null) throw BusinessException.collectionNotFound();
        // 聚合根校验 item 归属（不属于本聚合抛 COLLECTION_ITEM_NOT_FOUND）
        agg.updateItem(itemId,
                data != null ? jsonToString(data) : null,
                status);
        collectionRepository.save(agg);
        return CollectionItemResponse.fromEntity(
                collectionRepository.findItem(itemId, collectionId));
    }

    public void deleteItem(String siteId, String collectionId, String itemId) {
        CollectionAggregate agg = collectionRepository.findByIdWithItems(collectionId, siteId);
        if (agg == null) throw BusinessException.collectionNotFound();
        agg.removeItem(itemId);
        collectionRepository.save(agg);
    }

    // === 读模型（PublicCollectionPort） ===

    /**
     * 公开读 collection items（website SSR 用）。
     * 校验 collection 属于 slug 对应 site 且 status=active；只返回 active items。
     */
    @Override
    public List<CollectionItemResponse> listPublicItems(String slug, String collectionId) {
        com.luban.backend.shared.entity.Site site = siteRepository.findBySlug(slug)
                .map(com.luban.backend.shared.domain.SiteAggregate::toSite).orElse(null);
        if (site == null) throw BusinessException.siteNotFound();
        ContentCollection c = collectionRepository.findById(collectionId, site.getId());
        if (c == null || !"active".equals(c.getStatus())) {
            throw BusinessException.collectionNotFound();
        }
        return collectionRepository.listItems(collectionId).stream()
                .filter(it -> "active".equals(it.getStatus()))
                .map(CollectionItemResponse::fromEntity)
                .collect(Collectors.toList());
    }

    private boolean isDuplicate(DataIntegrityViolationException e) {
        return e.getMessage() != null && e.getMessage().contains("Duplicate");
    }

    private String jsonToString(JsonNode node) {
        if (node == null) return "{}";
        try {
            return JsonUtil.MAPPER.writeValueAsString(node);
        } catch (Exception e) {
            return "{}";
        }
    }
}

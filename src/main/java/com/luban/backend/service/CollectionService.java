package com.luban.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luban.backend.dto.CollectionItemResponse;
import com.luban.backend.dto.CollectionResponse;
import com.luban.backend.entity.Collection;
import com.luban.backend.entity.CollectionItem;
import com.luban.backend.exception.BusinessException;
import com.luban.backend.mapper.CollectionMapper;
import com.luban.backend.mapper.SiteMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * V2-T7 Collection CMS 服务。
 * Collection + CollectionItem CRUD；siteId tenant guard；name 冲突检测。
 */
@Service
public class CollectionService {

    private final CollectionMapper collectionMapper;
    private final SiteMapper siteMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CollectionService(CollectionMapper collectionMapper, SiteMapper siteMapper) {
        this.collectionMapper = collectionMapper;
        this.siteMapper = siteMapper;
    }

    // === Collection ===

    public List<CollectionResponse> list(String siteId) {
        if (siteMapper.getById(siteId) == null) throw BusinessException.siteNotFound();
        return collectionMapper.listBySiteId(siteId).stream()
            .map(CollectionResponse::fromEntity).collect(Collectors.toList());
    }

    public CollectionResponse get(String siteId, String id) {
        Collection c = collectionMapper.getByIdAndSiteId(id, siteId);
        if (c == null) throw BusinessException.collectionNotFound();
        return CollectionResponse.fromEntity(c);
    }

    public CollectionResponse create(String siteId, String name, JsonNode fieldSchema, String status) {
        if (siteMapper.getById(siteId) == null) throw BusinessException.siteNotFound();
        if (status == null || status.isBlank()) status = "active";
        Collection c = new Collection();
        c.setId(UUID.randomUUID().toString());
        c.setSiteId(siteId);
        c.setName(name);
        c.setFieldSchemaJson(jsonToString(fieldSchema));
        c.setStatus(status);
        Instant now = Instant.now();
        c.setCreatedAt(now);
        c.setUpdatedAt(now);
        try {
            collectionMapper.insert(c);
        } catch (DataIntegrityViolationException e) {
            if (e.getMessage() != null && e.getMessage().contains("Duplicate")) {
                throw BusinessException.collectionNameConflict();
            }
            throw e;
        }
        return CollectionResponse.fromEntity(c);
    }

    public CollectionResponse update(String siteId, String id, String name, JsonNode fieldSchema, String status) {
        Collection c = collectionMapper.getByIdAndSiteId(id, siteId);
        if (c == null) throw BusinessException.collectionNotFound();
        c.setName(name);
        if (fieldSchema != null) c.setFieldSchemaJson(jsonToString(fieldSchema));
        if (status != null) c.setStatus(status);
        c.setUpdatedAt(Instant.now());
        try {
            int n = collectionMapper.update(c);
            if (n == 0) throw BusinessException.collectionNotFound();
        } catch (DataIntegrityViolationException e) {
            if (e.getMessage() != null && e.getMessage().contains("Duplicate")) {
                throw BusinessException.collectionNameConflict();
            }
            throw e;
        }
        return CollectionResponse.fromEntity(c);
    }

    public void delete(String siteId, String id) {
        int n = collectionMapper.deleteByIdAndSiteId(id, siteId);
        if (n == 0) throw BusinessException.collectionNotFound();
        // collection_items 由 FK ON DELETE CASCADE 自动清理
    }

    // === CollectionItem ===

    public List<CollectionItemResponse> listItems(String siteId, String collectionId) {
        // 校验 collection 属于该 site
        Collection c = collectionMapper.getByIdAndSiteId(collectionId, siteId);
        if (c == null) throw BusinessException.collectionNotFound();
        return collectionMapper.listItemsByCollectionId(collectionId).stream()
            .map(CollectionItemResponse::fromEntity).collect(Collectors.toList());
    }

    public CollectionItemResponse getItem(String siteId, String collectionId, String itemId) {
        Collection c = collectionMapper.getByIdAndSiteId(collectionId, siteId);
        if (c == null) throw BusinessException.collectionNotFound();
        CollectionItem it = collectionMapper.getItemByIdAndCollectionId(itemId, collectionId);
        if (it == null) throw BusinessException.collectionItemNotFound();
        return CollectionItemResponse.fromEntity(it);
    }

    public CollectionItemResponse createItem(String siteId, String collectionId, JsonNode data, String status) {
        Collection c = collectionMapper.getByIdAndSiteId(collectionId, siteId);
        if (c == null) throw BusinessException.collectionNotFound();
        if (status == null || status.isBlank()) status = "active";
        CollectionItem it = new CollectionItem();
        it.setId(UUID.randomUUID().toString());
        it.setCollectionId(collectionId);
        it.setDataJson(jsonToString(data));
        it.setStatus(status);
        Instant now = Instant.now();
        it.setCreatedAt(now);
        it.setUpdatedAt(now);
        collectionMapper.insertItem(it);
        return CollectionItemResponse.fromEntity(it);
    }

    public CollectionItemResponse updateItem(String siteId, String collectionId, String itemId, JsonNode data, String status) {
        Collection c = collectionMapper.getByIdAndSiteId(collectionId, siteId);
        if (c == null) throw BusinessException.collectionNotFound();
        CollectionItem it = collectionMapper.getItemByIdAndCollectionId(itemId, collectionId);
        if (it == null) throw BusinessException.collectionItemNotFound();
        if (data != null) it.setDataJson(jsonToString(data));
        if (status != null) it.setStatus(status);
        it.setUpdatedAt(Instant.now());
        int n = collectionMapper.updateItem(it);
        if (n == 0) throw BusinessException.collectionItemNotFound();
        return CollectionItemResponse.fromEntity(it);
    }

    public void deleteItem(String siteId, String collectionId, String itemId) {
        Collection c = collectionMapper.getByIdAndSiteId(collectionId, siteId);
        if (c == null) throw BusinessException.collectionNotFound();
        int n = collectionMapper.deleteItemByIdAndCollectionId(itemId, collectionId);
        if (n == 0) throw BusinessException.collectionItemNotFound();
    }

    private String jsonToString(JsonNode node) {
        if (node == null) return "{}";
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            return "{}";
        }
    }
}

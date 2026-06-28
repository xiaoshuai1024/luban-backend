package com.luban.backend.operatorside.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luban.backend.shared.dto.PageResponse;
import com.luban.backend.shared.entity.Page;
import com.luban.backend.shared.entity.PublishedPage;
import com.luban.backend.shared.exception.BusinessException;
import com.luban.backend.shared.mapper.PageMapper;
import com.luban.backend.shared.mapper.PublishedPageMapper;
import com.luban.backend.shared.mapper.SiteMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PageService {

    private final PageMapper pageMapper;
    private final PublishedPageMapper publishedPageMapper;
    private final SiteMapper siteMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PageVersionService versionService;

    /** P0 status 白名单 */
    private static final List<String> VALID_STATUSES = List.of("draft", "published", "archived");

    public PageService(PageMapper pageMapper, PublishedPageMapper publishedPageMapper,
                       SiteMapper siteMapper, PageVersionService versionService) {
        this.pageMapper = pageMapper;
        this.publishedPageMapper = publishedPageMapper;
        this.siteMapper = siteMapper;
        this.versionService = versionService;
    }

    public List<PageResponse> list(String siteId) {
        if (siteMapper.getById(siteId) == null) throw BusinessException.siteNotFound();
        return pageMapper.listBySiteId(siteId).stream().map(PageResponse::fromEntity).collect(Collectors.toList());
    }

    public PageResponse get(String siteId, String pageId) {
        Page p = pageMapper.getByIdAndSiteId(pageId, siteId);
        if (p == null) throw BusinessException.pageNotFound();
        return PageResponse.fromEntity(p);
    }

    @Transactional(rollbackFor = Exception.class)
    public PageResponse create(String siteId, String name, String path, String status, JsonNode schema, JsonNode seo) {
        if (siteMapper.getById(siteId) == null) throw BusinessException.siteNotFound();
        if (status == null || status.isBlank()) status = "draft";
        validateStatus(status);
        String schemaJson = schemaToJson(schema);
        Page page = new Page();
        page.setId(UUID.randomUUID().toString());
        page.setSiteId(siteId);
        page.setName(name);
        page.setPath(path);
        page.setStatus(status);
        page.setSchemaJson(schemaJson);
        page.setSeoJson(jsonToString(seo));
        Instant now = Instant.now();
        page.setCreatedAt(now);
        page.setUpdatedAt(now);
        try {
            pageMapper.insert(page);
        } catch (DataIntegrityViolationException e) {
            if (e.getMessage() != null && e.getMessage().contains("Duplicate")) {
                throw BusinessException.pagePathConflict();
            }
            throw e;
        }
        // V2-T8：新建后生成首版快照（v1）
        versionService.createSnapshot(page.getId(), schema, "初始版本", null);
        return PageResponse.fromEntity(page);
    }

    @Transactional(rollbackFor = Exception.class)
    public PageResponse update(String siteId, String pageId, String name, String path, String status, JsonNode schema, JsonNode seo) {
        Page page = pageMapper.getByIdAndSiteId(pageId, siteId);
        if (page == null) throw BusinessException.pageNotFound();
        String oldStatus = page.getStatus();
        page.setName(name);
        page.setPath(path);
        page.setStatus(status != null ? status : page.getStatus());
        validateStatus(page.getStatus());
        page.setSchemaJson(schemaToJson(schema));
        // V2-T2: seo 为 null 表示不传（保留旧值）；空对象/有值才覆盖
        if (seo != null) {
            page.setSeoJson(jsonToString(seo));
        }
        page.setUpdatedAt(Instant.now());
        try {
            int n = pageMapper.update(page);
            if (n == 0) throw BusinessException.pageNotFound();
        } catch (DataIntegrityViolationException e) {
            if (e.getMessage() != null && e.getMessage().contains("Duplicate")) {
                throw BusinessException.pagePathConflict();
            }
            throw e;
        }
        // V2-T8：保存后生成快照（每次保存一条版本）
        versionService.createSnapshot(page.getId(), schema, "保存", null);
        // 契约一致性：PUT 更新 status 时同步 published_pages 快照，
        // 使「PUT status=published」与「POST /publish」行为一致（website 公开访问才能查到）。
        syncPublishedState(siteId, page, oldStatus);
        return PageResponse.fromEntity(page);
    }

    /**
     * 根据 pages.status 同步 published_pages 快照。
     * - status 变为 published：写入快照（复用 publish 快照构造）
     * - status 从 published 变为其他：清理快照（等同下线）
     * 保持「无论用 PUT 还是 POST /publish，公开访问行为一致」。
     */
    private void syncPublishedState(String siteId, Page page, String oldStatus) {
        String newStatus = page.getStatus();
        boolean nowPublished = "published".equals(newStatus);
        boolean wasPublished = "published".equals(oldStatus);
        if (nowPublished && !wasPublished) {
            // 草稿→发布：写入快照（字段对齐 publish() 方法，name/publishedBy 必填）
            publishedPageMapper.deleteByPageIdAndSiteId(page.getId(), siteId);
            PublishedPage snapshot = new PublishedPage();
            snapshot.setId(java.util.UUID.randomUUID().toString());
            snapshot.setSiteId(siteId);
            snapshot.setPageId(page.getId());
            snapshot.setName(page.getName());
            snapshot.setPath(page.getPath());
            snapshot.setSchemaJson(page.getSchemaJson());
            snapshot.setSeoJson(page.getSeoJson());
            snapshot.setPublishedAt(java.time.Instant.now());
            snapshot.setPublishedBy(null);
            try {
                publishedPageMapper.insert(snapshot);
            } catch (org.springframework.dao.DataIntegrityViolationException e) {
                if (e.getMessage() != null && e.getMessage().contains("Duplicate")) {
                    throw BusinessException.pagePathConflict();
                }
                throw e;
            }
        } else if (!nowPublished && wasPublished) {
            // 发布→下线：清理快照
            publishedPageMapper.deleteByPageIdAndSiteId(page.getId(), siteId);
        }
    }

    public void delete(String siteId, String pageId) {
        // P0：删除时同步清理 published_pages 快照
        publishedPageMapper.deleteByPageIdAndSiteId(pageId, siteId);
        int n = pageMapper.deleteByIdAndSiteId(pageId, siteId);
        if (n == 0) throw BusinessException.pageNotFound();
    }

    // ==================== P0 发布闭环 ====================

    /**
     * 发布页面：从草稿拷贝到 published_pages，更新 status=published。
     *
     * @param siteId    站点 ID
     * @param pageId    页面 ID
     * @param publishedBy 发布人（来自 UserContext）
     * @return 更新后的 PageResponse
     */
    @Transactional(rollbackFor = Exception.class)
    public PageResponse publish(String siteId, String pageId, String publishedBy) {
        Page page = pageMapper.getByIdAndSiteId(pageId, siteId);
        if (page == null) throw BusinessException.pageNotFound();

        Instant now = Instant.now();

        // 1. 删除旧发布快照（处理重新发布）
        publishedPageMapper.deleteByPageId(pageId);

        // 2. 从草稿拷贝到 published_pages
        PublishedPage snapshot = new PublishedPage();
        snapshot.setId(UUID.randomUUID().toString());
        snapshot.setPageId(pageId);
        snapshot.setSiteId(siteId);
        snapshot.setName(page.getName());
        snapshot.setPath(page.getPath());
        snapshot.setSchemaJson(page.getSchemaJson());
        snapshot.setSeoJson(page.getSeoJson());
        snapshot.setPublishedAt(now);
        snapshot.setPublishedBy(publishedBy);
        try {
            publishedPageMapper.insert(snapshot);
        } catch (DataIntegrityViolationException e) {
            if (e.getMessage() != null && e.getMessage().contains("Duplicate")) {
                throw BusinessException.pagePathConflict();
            }
            throw e;
        }

        // 3. 更新 pages 表 status=published + 审计字段
        pageMapper.updatePublishStatus(pageId, siteId, "published", now, publishedBy, now);
        page.setStatus("published");
        page.setPublishedAt(now);
        page.setPublishedBy(publishedBy);
        page.setUpdatedAt(now);

        // 4. 打发布版本快照
        try {
            versionService.createSnapshot(pageId,
                    objectMapper.readTree(page.getSchemaJson()), "发布", publishedBy);
        } catch (Exception e) {
            // 快照失败不阻塞发布流程，但记录警告
            System.err.println("[WARN] 发布快照创建失败 pageId=" + pageId + ": " + e.getMessage());
        }

        return PageResponse.fromEntity(page);
    }

    /**
     * 下线页面：删除 published_pages 快照，更新 status=archived。
     */
    @Transactional(rollbackFor = Exception.class)
    public PageResponse unpublish(String siteId, String pageId) {
        Page page = pageMapper.getByIdAndSiteId(pageId, siteId);
        if (page == null) throw BusinessException.pageNotFound();

        // 1. 删除发布快照
        publishedPageMapper.deleteByPageIdAndSiteId(pageId, siteId);

        // 2. 更新 status=archived
        Instant now = Instant.now();
        pageMapper.updatePublishStatus(pageId, siteId, "archived", page.getPublishedAt(), page.getPublishedBy(), now);
        page.setStatus("archived");
        page.setUpdatedAt(now);

        return PageResponse.fromEntity(page);
    }

    /**
     * 草稿预览：直接返回 pages 表的草稿内容（不读 published_pages）。
     */
    public PageResponse getPreviewDraft(String siteId, String pageId) {
        Page page = pageMapper.getByIdAndSiteId(pageId, siteId);
        if (page == null) throw BusinessException.pageNotFound();
        return PageResponse.fromEntity(page);
    }

    // ==================== 内部 helpers ====================

    /** P0：status 白名单校验。 */
    private void validateStatus(String status) {
        if (status != null && !VALID_STATUSES.contains(status)) {
            throw BusinessException.invalidArgument("非法 status 值: " + status + "，允许: " + VALID_STATUSES);
        }
    }

    private String schemaToJson(JsonNode schema) {
        if (schema == null) return "{}";
        try {
            return objectMapper.writeValueAsString(schema);
        } catch (Exception e) {
            return "{}";
        }
    }

    /** V2-T2: SEO JsonNode → 字符串；null 返回 null（保留旧值语义在调用处判定）。 */
    private String jsonToString(JsonNode node) {
        if (node == null) return null;
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            return null;
        }
    }
}

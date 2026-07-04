package com.luban.backend.operatorside.service;
import com.luban.backend.shared.util.JsonUtil;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luban.backend.operatorside.repository.PublishedPageProjection;
import com.luban.backend.shared.auth.UserContext;
import com.luban.backend.shared.domain.PageAggregate;
import com.luban.backend.shared.dto.PageResponse;
import com.luban.backend.shared.entity.Page;
import com.luban.backend.shared.exception.BusinessException;
import com.luban.backend.shared.mapper.SiteMapper;
import com.luban.backend.shared.repository.PageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 页面应用服务（backend-ddd-refactor plan v2 T6）。
 *
 * <p>用例编排：加载聚合根 → 调聚合根方法 → 保存 → 发布事件。双写一致性（PUT published ≡ POST /publish）
 * 经 {@link PageAggregate#publish} 统一入口 + {@link PublishedPageProjection}（事务内投影）保证。
 *
 * <p>v2 DDD 改造：{@code pageMapper} → {@link PageRepository}；{@code publishedPageMapper} →
 * {@link PublishedPageProjection}（投影协作者，事务内双写一致性，非 AFTER_COMMIT 事件）；保留
 * {@code siteMapper}（SITE_NOT_FOUND 种子校验，ArchUnit 白名单豁免）。零写侧 Mapper 依赖。
 *
 * <p>修复生产级问题（对齐工程原则）：
 * <ul>
 *   <li>syncPublishedState 旧 publishedBy=null 不一致 → 聚合根统一用 UserContext actor（"system" 兜底）</li>
 *   <li>publish 快照失败旧用 System.err.println 吞 → 改用 SLF4J Logger（保持"不阻塞发布"行为但生产化）</li>
 *   <li>status 校验由白名单升级为聚合根状态机（显式转换校验）</li>
 *   <li>delete 缺 @Transactional → 已补（published_pages 清理 + pages 删除原子）</li>
 * </ul>
 */
@Service
public class PageService {

    private static final Logger log = LoggerFactory.getLogger(PageService.class);

    private final PageRepository pageRepository;
    private final PublishedPageProjection publishedPageProjection;
    private final SiteMapper siteMapper;
    
    private final PageVersionService versionService;

    public PageService(PageRepository pageRepository, PublishedPageProjection publishedPageProjection,
                       SiteMapper siteMapper, PageVersionService versionService) {
        this.pageRepository = pageRepository;
        this.publishedPageProjection = publishedPageProjection;
        this.siteMapper = siteMapper;
        this.versionService = versionService;
    }

    public List<PageResponse> list(String siteId) {
        if (siteMapper.getById(siteId) == null) throw BusinessException.siteNotFound();
        return pageRepository.listBySiteId(siteId).stream().map(PageResponse::fromEntity).collect(Collectors.toList());
    }

    public PageResponse get(String siteId, String pageId) {
        Page p = pageRepository.findById(pageId, siteId)
                .map(PageAggregate::toPage)
                .orElse(null);
        if (p == null) throw BusinessException.pageNotFound();
        return PageResponse.fromEntity(p);
    }

    @Transactional(rollbackFor = Exception.class)
    public PageResponse create(String siteId, String name, String path, String status, JsonNode schema, JsonNode seo) {
        if (siteMapper.getById(siteId) == null) throw BusinessException.siteNotFound();
        // 聚合根工厂默认 draft；status 非 draft 时校验后设（聚合根状态机守护）
        PageAggregate agg = PageAggregate.newPage(
                UUID.randomUUID().toString(), siteId, name, path, schemaToJson(schema), jsonToString(seo));
        if (status != null && !status.isBlank() && !"draft".equals(status)) {
            validateStatus(status);
            agg.toPage().setStatus(status);
        }
        try {
            pageRepository.save(agg);
        } catch (DataIntegrityViolationException e) {
            if (isDuplicate(e)) throw BusinessException.pagePathConflict();
            throw e;
        }
        versionService.createSnapshot(agg.toPage().getId(), schema, "初始版本", null);
        return PageResponse.fromEntity(agg.toPage());
    }

    @Transactional(rollbackFor = Exception.class)
    public PageResponse update(String siteId, String pageId, String name, String path, String status, JsonNode schema, JsonNode seo) {
        PageAggregate agg = pageRepository.findById(pageId, siteId)
                .orElseThrow(BusinessException::pageNotFound);
        Page page = agg.toPage();
        String oldStatus = page.getStatus();

        // 聚合根 update（patch 语义）
        agg.update(name, path, schemaToJson(schema), seo != null ? jsonToString(seo) : null);
        // status 转换（聚合根状态机守护）
        if (status != null && !status.equals(oldStatus)) {
            validateStatus(status);
            applyStatusTransition(agg, status);
        }
        try {
            pageRepository.save(agg);
        } catch (DataIntegrityViolationException e) {
            if (isDuplicate(e)) throw BusinessException.pagePathConflict();
            throw e;
        }
        versionService.createSnapshot(page.getId(), schema, "保存", null);
        // 双写一致性：PUT 改 status 时由投影协作者同步 published_pages（事务内，聚合根产快照含 actor）
        publishedPageProjection.syncOnStatusChange(siteId, agg, oldStatus);
        return PageResponse.fromEntity(agg.toPage());
    }

    /**
     * PUT 触发的状态转换：用聚合根 publish/unpublish/archive 统一入口，
     * 保证 PUT published ≡ POST /publish（含 publishedBy actor，修复旧 null 不一致）。
     */
    private void applyStatusTransition(PageAggregate agg, String targetStatus) {
        switch (targetStatus) {
            case "published" -> agg.publish(resolveActor());
            case "archived" -> {
                if ("published".equals(agg.toPage().getStatus())) {
                    agg.unpublish();
                } else {
                    agg.archive();
                }
            }
            default -> agg.toPage().setStatus(targetStatus);   // draft 等（聚合根已校验白名单）
        }
    }

    /** 解析当前操作 actor（PUT 发布记录真实 actor，修复旧 publishedBy=null）。 */
    private String resolveActor() {
        String actor = UserContext.getUserId();
        return actor != null ? actor : "system";
    }

    /**
     * 根据 pages.status 同步 published_pages 快照的逻辑已移至 {@link PublishedPageProjection#syncOnStatusChange}，
     * 由投影协作者封装（保持事务内双写一致性，PageService 不直接依赖 Mapper）。
     */

    @Transactional(rollbackFor = Exception.class)
    public void delete(String siteId, String pageId) {
        // P0：删除时同步清理 published_pages 快照（与 pages 删除原子，事务保证）
        publishedPageProjection.removeByPageAndSite(pageId, siteId);
        int n = pageRepository.deleteByIdAndSiteId(pageId, siteId);
        if (n == 0) throw BusinessException.pageNotFound();
    }

    // ==================== P0 发布闭环 ====================

    /**
     * 发布页面（聚合根统一入口，PUT/POST 共用）。
     *
     * @param siteId      站点 ID
     * @param pageId      页面 ID
     * @param publishedBy 发布人（来自 UserContext）
     */
    @Transactional(rollbackFor = Exception.class)
    public PageResponse publish(String siteId, String pageId, String publishedBy) {
        PageAggregate agg = pageRepository.findById(pageId, siteId)
                .orElseThrow(BusinessException::pageNotFound);
        Page page = agg.toPage();

        agg.publish(publishedBy);   // 聚合根状态机 + 审计字段 + 发事件

        // 投影协作者写 published_pages 快照（事务内双写一致性，聚合根产快照含 actor）
        publishedPageProjection.upsertOnPublish(agg);

        pageRepository.updatePublishStatus(pageId, siteId, "published",
                page.getPublishedAt(), publishedBy, agg.toPage().getUpdatedAt());

        // 发布版本快照（失败用 Logger 记录，不阻塞发布——保持原行为但生产化）
        try {
            versionService.createSnapshot(pageId,
                    JsonUtil.MAPPER.readTree(page.getSchemaJson()), "发布", publishedBy);
        } catch (Exception e) {
            log.warn("发布快照创建失败 pageId={}: {}", pageId, e.getMessage());
        }
        return PageResponse.fromEntity(agg.toPage());
    }

    /** 下线页面（聚合根 unpublish）。 */
    @Transactional(rollbackFor = Exception.class)
    public PageResponse unpublish(String siteId, String pageId) {
        PageAggregate agg = pageRepository.findById(pageId, siteId)
                .orElseThrow(BusinessException::pageNotFound);
        Page page = agg.toPage();

        agg.unpublish();   // 聚合根状态机 + 发事件

        publishedPageProjection.removeByPageAndSite(pageId, siteId);
        pageRepository.updatePublishStatus(pageId, siteId, "archived",
                page.getPublishedAt(), page.getPublishedBy(), agg.toPage().getUpdatedAt());
        return PageResponse.fromEntity(agg.toPage());
    }

    /**
     * 草稿预览：直接返回 pages 表的草稿内容（不读 published_pages）。
     */
    public PageResponse getPreviewDraft(String siteId, String pageId) {
        Page page = pageRepository.findById(pageId, siteId)
                .map(PageAggregate::toPage)
                .orElse(null);
        if (page == null) throw BusinessException.pageNotFound();
        return PageResponse.fromEntity(page);
    }

    // ==================== 内部 helpers ====================

    /** status 白名单校验（用聚合根的 VALID_STATUSES，统一来源）。 */
    private void validateStatus(String status) {
        if (status != null && !PageAggregate.VALID_STATUSES.contains(status)) {
            throw BusinessException.invalidArgument("非法 status 值: " + status + "，允许: " + PageAggregate.VALID_STATUSES);
        }
    }

    /** UNIQUE 冲突嗅探（MySQL "Duplicate" / H2 "Unique index"/"primary key violation"）。 */
    private static boolean isDuplicate(DataIntegrityViolationException e) {
        if (e == null || e.getMessage() == null) return false;
        String m = e.getMessage();
        return m.contains("Duplicate") || m.contains("Unique index") || m.contains("primary key violation");
    }

    private String schemaToJson(JsonNode schema) {
        if (schema == null) return "{}";
        try {
            return JsonUtil.MAPPER.writeValueAsString(schema);
        } catch (Exception e) {
            return "{}";
        }
    }

    /** V2-T2: SEO JsonNode → 字符串；null 返回 null（保留旧值语义在调用处判定）。 */
    private String jsonToString(JsonNode node) {
        if (node == null) return null;
        try {
            return JsonUtil.MAPPER.writeValueAsString(node);
        } catch (Exception e) {
            return null;
        }
    }
}

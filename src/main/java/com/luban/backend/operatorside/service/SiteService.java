package com.luban.backend.operatorside.service;

import com.luban.backend.shared.domain.SiteAggregate;
import com.luban.backend.shared.dto.SiteResponse;
import com.luban.backend.shared.entity.Site;
import com.luban.backend.shared.exception.BusinessException;
import com.luban.backend.shared.mapper.CampaignMapper;
import com.luban.backend.shared.mapper.ChannelMapper;
import com.luban.backend.shared.mapper.CollectionMapper;
import com.luban.backend.shared.mapper.DatasourceMapper;
import com.luban.backend.shared.mapper.FormMapper;
import com.luban.backend.shared.mapper.LeadMapper;
import com.luban.backend.shared.mapper.PageMapper;
import com.luban.backend.shared.mapper.SiteMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 站点应用服务（backend-ddd-refactor plan v2 T5）。
 *
 * <p>用例编排：加载聚合根 → 调聚合根方法 → 保存 → 发布事件。
 * 7 表级联删除事务边界：delete 在 @Transactional 内按 FK 顺序清子表（聚合根定义事件 + 顺序契约，
 * Mapper 调用保留在 Service 经 freeze 兜底——子表属其它聚合，SiteRepository 不应依赖 7 个跨聚合 Mapper）。
 */
@Service
public class SiteService {

    private final SiteMapper siteMapper;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
    // V2 级联删除：删除站点前需先清理子表（FK RESTRICT）
    private final PageMapper pageMapper;
    private final FormMapper formMapper;
    private final LeadMapper leadMapper;
    private final DatasourceMapper datasourceMapper;
    private final CollectionMapper collectionMapper;
    // app-deeplink-backend-arch T8：短链子表也需级联清理
    private final ChannelMapper channelMapper;
    private final CampaignMapper campaignMapper;
    private final ApplicationEventPublisher eventPublisher;

    public SiteService(SiteMapper siteMapper, PageMapper pageMapper, FormMapper formMapper,
                       LeadMapper leadMapper, DatasourceMapper datasourceMapper, CollectionMapper collectionMapper,
                       ChannelMapper channelMapper, CampaignMapper campaignMapper,
                       ApplicationEventPublisher eventPublisher) {
        this.siteMapper = siteMapper;
        this.pageMapper = pageMapper;
        this.formMapper = formMapper;
        this.leadMapper = leadMapper;
        this.datasourceMapper = datasourceMapper;
        this.collectionMapper = collectionMapper;
        this.channelMapper = channelMapper;
        this.campaignMapper = campaignMapper;
        this.eventPublisher = eventPublisher;
    }

    public List<SiteResponse> list() {
        return siteMapper.list().stream().map(SiteResponse::fromEntity).collect(Collectors.toList());
    }

    public SiteResponse get(String id) {
        Site s = siteMapper.getById(id);
        if (s == null) throw BusinessException.siteNotFound();
        return SiteResponse.fromEntity(s);
    }

    public SiteResponse create(String name, String slug, String baseUrl, String status) {
        // 聚合根工厂默认 status=active（status 参数兼容旧接口，仅 active 有效）
        SiteAggregate agg = SiteAggregate.newSite(
                UUID.randomUUID().toString(), name, slug, baseUrl != null ? baseUrl : "");
        if (status != null && !status.isBlank()) {
            agg.toSite().setStatus(status);
        }
        try {
            siteMapper.insert(agg.toSite());
        } catch (DataIntegrityViolationException e) {
            if (isUniqueViolation(e)) {
                throw BusinessException.slugConflict();
            }
            throw e;
        }
        return SiteResponse.fromEntity(agg.toSite());
    }

    public SiteResponse update(String id, String name, String slug, String baseUrl, String status,
                               com.fasterxml.jackson.databind.JsonNode seo, com.fasterxml.jackson.databind.JsonNode analytics) {
        Site site = siteMapper.getById(id);
        if (site == null) throw BusinessException.siteNotFound();
        SiteAggregate agg = SiteAggregate.reconstitute(site);
        agg.toSite().setSlug(slug);
        agg.toSite().setStatus(status);
        if (seo != null) agg.toSite().setSeoJson(jsonToString(seo));
        if (analytics != null) agg.toSite().setAnalyticsJson(jsonToString(analytics));
        agg.update(name, baseUrl != null ? baseUrl : "", null);
        try {
            int n = siteMapper.update(agg.toSite());
            if (n == 0) throw BusinessException.siteNotFound();
        } catch (DataIntegrityViolationException e) {
            if (isUniqueViolation(e)) {
                throw BusinessException.slugConflict();
            }
            throw e;
        }
        return SiteResponse.fromEntity(agg.toSite());
    }

    /** V2-T10: JsonNode → 字符串；null 返回 null（保留旧值语义） */
    private String jsonToString(com.fasterxml.jackson.databind.JsonNode node) {
        if (node == null) return null;
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Detect a UNIQUE-constraint violation across DB drivers:
     *   - MySQL: "Duplicate entry ..." (matches Go isDuplicateErr in site_repo.go)
     *   - H2 (MySQL mode, used in tests): "Unique index or primary key violation"
     *
     * Behavior on real MySQL is byte-identical to the prior "Duplicate" check.
     */
    private static boolean isUniqueViolation(DataIntegrityViolationException e) {
        if (e == null || e.getMessage() == null) return false;
        String m = e.getMessage();
        return m.contains("Duplicate") || m.contains("Unique index") || m.contains("primary key violation");
    }

    /**
     * V2 级联删除：先清理所有子表（leads/forms/datasources/collections/pages/channels/campaigns），
     * 再删 site。pages 删除后 page_versions 由 FK CASCADE 自动清。
     * 解决删除站点时 FK RESTRICT 报 500 的问题。
     * Wave 2 致命修复：加 @Transactional 防止中途失败留孤儿数据。
     * app-deeplink-backend-arch T8：扩展级联含 channels/campaigns（短链子表）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        Site site = siteMapper.getById(id);
        if (site == null) throw BusinessException.siteNotFound();
        // 聚合根发 SiteDeletedEvent（供 Analytics/Campaign 等清理关联数据）
        SiteAggregate agg = SiteAggregate.reconstitute(site);
        agg.delete();
        // 7 表级联删除（按 FK 顺序，事务边界由 @Transactional 保证）
        leadMapper.deleteBySiteId(id);
        formMapper.deleteBySiteId(id);
        datasourceMapper.deleteBySiteId(id);
        collectionMapper.deleteBySiteId(id);
        channelMapper.deleteBySiteId(id);   // channels 先删（FK 引用 campaigns）
        campaignMapper.deleteBySiteId(id);  // campaigns 后删
        pageMapper.deleteBySiteId(id);      // pages 删后 page_versions 经 FK CASCADE 自动清
        int n = siteMapper.deleteById(id);
        if (n == 0) throw BusinessException.siteNotFound();
        // 事务提交后发布事件（AFTER_COMMIT 由 @TransactionalEventListener 消费）
        agg.pullEvents().forEach(eventPublisher::publishEvent);
    }
}

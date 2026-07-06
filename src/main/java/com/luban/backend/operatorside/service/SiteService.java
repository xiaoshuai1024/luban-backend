package com.luban.backend.operatorside.service;

import com.luban.backend.operatorside.repository.SiteCascadeDeleter;
import com.luban.backend.shared.domain.SiteAggregate;
import com.luban.backend.shared.dto.SiteResponse;
import com.luban.backend.shared.exception.BusinessException;
import com.luban.backend.shared.repository.SiteRepository;
import com.luban.backend.shared.support.DomainEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 站点应用服务（backend-ddd-refactor plan v2 T5）。
 *
 * <p>用例编排：加载聚合根 → 调聚合根方法 → 保存 → 发布事件。零 Mapper 依赖——
 * 7 表级联删除由 {@link SiteCascadeDeleter}（持久化协作者）执行，保持 Service 薄。
 */
@Service
public class SiteService {

    private final SiteRepository siteRepository;
    private final SiteCascadeDeleter cascadeDeleter;
    
    private final DomainEventPublisher eventPublisher;

    public SiteService(SiteRepository siteRepository, SiteCascadeDeleter cascadeDeleter,
                       DomainEventPublisher eventPublisher) {
        this.siteRepository = siteRepository;
        this.cascadeDeleter = cascadeDeleter;
        this.eventPublisher = eventPublisher;
    }

    public List<SiteResponse> list() {
        return siteRepository.list().stream().map(SiteResponse::fromEntity).collect(Collectors.toList());
    }

    public SiteResponse get(String id) {
        SiteAggregate agg = siteRepository.findById(id)
                .orElseThrow(BusinessException::siteNotFound);
        return SiteResponse.fromEntity(agg.toSite());
    }

    public SiteResponse create(String name, String slug, String baseUrl, String status) {
        SiteAggregate agg = SiteAggregate.newSite(
                UUID.randomUUID().toString(), name, slug, baseUrl != null ? baseUrl : "");
        if (status != null && !status.isBlank()) {
            agg.toSite().setStatus(status);
        }
        try {
            siteRepository.save(agg);
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
        SiteAggregate agg = siteRepository.findById(id)
                .orElseThrow(BusinessException::siteNotFound);
        agg.toSite().setSlug(slug);
        agg.toSite().setStatus(status);
        if (seo != null) agg.toSite().setSeoJson(com.luban.backend.shared.util.JsonUtil.toString(seo));
        if (analytics != null) agg.toSite().setAnalyticsJson(com.luban.backend.shared.util.JsonUtil.toString(analytics));
        agg.update(name, baseUrl != null ? baseUrl : "", null);
        try {
            siteRepository.save(agg);
        } catch (DataIntegrityViolationException e) {
            if (isUniqueViolation(e)) {
                throw BusinessException.slugConflict();
            }
            throw e;
        }
        return SiteResponse.fromEntity(agg.toSite());
    }

    /** V2-T10: JsonNode → 字符串；null 返回 null（保留旧值语义） */
    

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
     * V2 级联删除：先由 {@link SiteCascadeDeleter} 清理所有子表（leads/forms/datasources/
     * collections/channels/campaigns/pages），再删 site。pages 删除后 page_versions 由 FK CASCADE 自动清。
     * 事务边界由 {@code @Transactional} 保证；AFTER_COMMIT 发布 {@code SiteDeletedEvent}。
     * app-deeplink-backend-arch T8：级联含 channels/campaigns（短链子表）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        SiteAggregate agg = siteRepository.findById(id)
                .orElseThrow(BusinessException::siteNotFound);
        // 聚合根发 SiteDeletedEvent（供 Analytics/Campaign 等清理关联数据）
        agg.delete();
        // 7 表级联删除（FK 顺序由协作者封装，事务边界由本方法 @Transactional 保证）
        cascadeDeleter.deleteChildren(id);
        siteRepository.deleteById(id);
        // 事务提交后发布事件（AFTER_COMMIT 由 @TransactionalEventListener 消费）
        eventPublisher.publishAll(agg.pullEvents());
    }
}

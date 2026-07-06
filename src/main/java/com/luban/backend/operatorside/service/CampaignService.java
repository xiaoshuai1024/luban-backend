package com.luban.backend.operatorside.service;

import com.luban.backend.shared.domain.CampaignAggregate;
import com.luban.backend.shared.dto.CampaignResponse;
import com.luban.backend.shared.dto.CampaignSaveRequest;
import com.luban.backend.shared.entity.Campaign;
import com.luban.backend.shared.exception.BusinessException;
import com.luban.backend.shared.repository.CampaignRepository;
import com.luban.backend.shared.repository.SiteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Campaign 活动领域服务（app-deeplink-backend-arch plan T13）。
 *
 * <p>运营端 CRUD，经 {@link CampaignAggregate} 聚合根校验状态机 + 时间窗 invariant。
 *
 * <p>backend-ddd-refactor plan v2：改经 {@link CampaignRepository}，不再直接依赖 CampaignMapper /
 * ChannelMapper（跨聚合 channel 查询封装在 repo.hasChannels）。
 */
@Service
public class CampaignService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CampaignService.class);

    private final CampaignRepository campaignRepository;
    private final SiteRepository siteRepository;
    private final TenantGuardService tenantGuard;
    private final com.luban.backend.shared.support.DomainEventPublisher eventPublisher;

    public CampaignService(CampaignRepository campaignRepository, SiteRepository siteRepository,
                           TenantGuardService tenantGuard,
                           com.luban.backend.shared.support.DomainEventPublisher eventPublisher) {
        this.campaignRepository = campaignRepository;
        this.siteRepository = siteRepository;
        this.tenantGuard = tenantGuard;
        this.eventPublisher = eventPublisher;
    }

    /** 拉取并发布聚合根累积的领域事件（AFTER_COMMIT 由 handler 消费）。 */
    private void publishEvents(com.luban.backend.shared.domain.CampaignAggregate agg) {
        eventPublisher.publishAll(agg.pullEvents());
    }

    /** 站点存在性 + 归属校验 */
    private void ensureSite(String siteId) {
        if (!siteRepository.existsById(siteId)) {
            log.warn("站点不存在 siteId={}（疑似越权访问）", siteId);
            throw BusinessException.siteNotFound();
        }
        tenantGuard.ensureSiteAccess(siteId);
    }

    public List<CampaignResponse> list(String siteId) {
        ensureSite(siteId);
        return campaignRepository.listBySiteId(siteId).stream()
                .map(CampaignResponse::fromEntity).collect(Collectors.toList());
    }

    public CampaignResponse get(String siteId, String id) {
        ensureSite(siteId);
        CampaignAggregate agg = campaignRepository.findById(id, siteId)
                .orElseThrow(BusinessException::campaignNotFound);
        return CampaignResponse.fromEntity(agg.toCampaign());
    }

    @Transactional(rollbackFor = Exception.class)
    public CampaignResponse create(CampaignSaveRequest req) {
        ensureSite(req.siteId());
        // 工厂内含校验（时间窗 + name 非空），状态机初始 planned 由聚合根守护
        CampaignAggregate agg = CampaignAggregate.newCampaign(
                UUID.randomUUID().toString(), req.siteId(), req.name(), req.startAt(), req.endAt());
        campaignRepository.save(agg);
        return CampaignResponse.fromEntity(agg.toCampaign());
    }

    @Transactional(rollbackFor = Exception.class)
    public CampaignResponse update(String siteId, String id, CampaignSaveRequest req) {
        ensureSite(siteId);
        CampaignAggregate agg = campaignRepository.findById(id, siteId)
                .orElseThrow(BusinessException::campaignNotFound);

        Campaign existing = agg.toCampaign();
        agg.update(req.name(), req.startAt(), req.endAt());
        if (req.status() != null && !req.status().equals(existing.getStatus())) {
            agg.transition(req.status());   // 聚合根状态机
        }
        campaignRepository.save(agg);
        publishEvents(agg);
        return CampaignResponse.fromEntity(agg.toCampaign());
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(String siteId, String id) {
        ensureSite(siteId);
        // 有 channel 拒绝删除：聚合根断言决策（跨聚合查询由 repo.hasChannels 封装）
        CampaignAggregate agg = campaignRepository.findById(id, siteId)
                .orElseThrow(BusinessException::campaignNotFound);
        agg.assertDeletable(campaignRepository.hasChannels(id));
        campaignRepository.deleteByIdAndSiteId(id, siteId);
    }
}

package com.luban.backend.operatorside.service;

import com.luban.backend.shared.domain.CampaignAggregate;
import com.luban.backend.shared.dto.CampaignResponse;
import com.luban.backend.shared.dto.CampaignSaveRequest;
import com.luban.backend.shared.entity.Campaign;
import com.luban.backend.shared.exception.BusinessException;
import com.luban.backend.shared.mapper.CampaignMapper;
import com.luban.backend.shared.mapper.ChannelMapper;
import com.luban.backend.shared.mapper.SiteMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Campaign 活动领域服务（app-deeplink-backend-arch plan T13）。
 *
 * <p>运营端 CRUD，经 {@link CampaignAggregate} 聚合根校验状态机 + 时间窗 invariant。
 */
@Service
public class CampaignService {

    private final CampaignMapper campaignMapper;
    private final SiteMapper siteMapper;
    private final ChannelMapper channelMapper;
    private final TenantGuardService tenantGuard;

    public CampaignService(CampaignMapper campaignMapper, SiteMapper siteMapper,
                           ChannelMapper channelMapper, TenantGuardService tenantGuard) {
        this.campaignMapper = campaignMapper;
        this.siteMapper = siteMapper;
        this.channelMapper = channelMapper;
        this.tenantGuard = tenantGuard;
    }

    /** 站点存在性 + 归属校验 */
    private void ensureSite(String siteId) {
        if (siteMapper.getById(siteId) == null) throw BusinessException.siteNotFound();
        tenantGuard.ensureSiteAccess(siteId);
    }

    public List<CampaignResponse> list(String siteId) {
        ensureSite(siteId);
        return campaignMapper.listBySiteId(siteId).stream()
                .map(CampaignResponse::fromEntity).collect(Collectors.toList());
    }

    public CampaignResponse get(String siteId, String id) {
        ensureSite(siteId);
        Campaign c = campaignMapper.getByIdAndSiteId(id, siteId);
        if (c == null) throw BusinessException.campaignNotFound();
        return CampaignResponse.fromEntity(c);
    }

    @Transactional(rollbackFor = Exception.class)
    public CampaignResponse create(CampaignSaveRequest req) {
        ensureSite(req.siteId());
        // 工厂内含校验（时间窗 + name 非空），状态机初始 planned 由聚合根守护
        CampaignAggregate agg = CampaignAggregate.newCampaign(
                UUID.randomUUID().toString(), req.siteId(), req.name(), req.startAt(), req.endAt());
        campaignMapper.insert(agg.toCampaign());
        return CampaignResponse.fromEntity(agg.toCampaign());
    }

    @Transactional(rollbackFor = Exception.class)
    public CampaignResponse update(String siteId, String id, CampaignSaveRequest req) {
        ensureSite(siteId);
        Campaign existing = campaignMapper.getByIdAndSiteId(id, siteId);
        if (existing == null) throw BusinessException.campaignNotFound();

        CampaignAggregate agg = CampaignAggregate.reconstitute(existing, null);
        agg.update(req.name(), req.startAt(), req.endAt());
        if (req.status() != null && !req.status().equals(existing.getStatus())) {
            agg.transition(req.status());   // 聚合根状态机
        }
        campaignMapper.update(agg.toCampaign());
        return CampaignResponse.fromEntity(agg.toCampaign());
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(String siteId, String id) {
        ensureSite(siteId);
        Campaign existing = campaignMapper.getByIdAndSiteId(id, siteId);
        if (existing == null) throw BusinessException.campaignNotFound();
        // 有 channel 拒绝删除：聚合根断言决策（跨聚合查询 Service 传入 boolean）
        CampaignAggregate agg = CampaignAggregate.reconstitute(existing, null);
        agg.assertDeletable(!channelMapper.listByCampaignId(id).isEmpty());
        campaignMapper.deleteByIdAndSiteId(id, siteId);
    }
}

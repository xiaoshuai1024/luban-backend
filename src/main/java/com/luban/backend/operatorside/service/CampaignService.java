package com.luban.backend.operatorside.service;

import com.luban.backend.shared.domain.CampaignAggregate;
import com.luban.backend.shared.dto.CampaignResponse;
import com.luban.backend.shared.dto.CampaignSaveRequest;
import com.luban.backend.shared.entity.Campaign;
import com.luban.backend.shared.exception.BusinessException;
import com.luban.backend.shared.mapper.CampaignMapper;
import com.luban.backend.shared.mapper.SiteMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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

    public CampaignService(CampaignMapper campaignMapper, SiteMapper siteMapper) {
        this.campaignMapper = campaignMapper;
        this.siteMapper = siteMapper;
    }

    public List<CampaignResponse> list(String siteId) {
        if (siteMapper.getById(siteId) == null) throw BusinessException.siteNotFound();
        return campaignMapper.listBySiteId(siteId).stream()
                .map(CampaignResponse::fromEntity).collect(Collectors.toList());
    }

    public CampaignResponse get(String siteId, String id) {
        Campaign c = campaignMapper.getByIdAndSiteId(id, siteId);
        if (c == null) throw BusinessException.campaignNotFound();
        return CampaignResponse.fromEntity(c);
    }

    @Transactional(rollbackFor = Exception.class)
    public CampaignResponse create(CampaignSaveRequest req) {
        if (siteMapper.getById(req.siteId()) == null) throw BusinessException.siteNotFound();
        CampaignAggregate.validateCampaignCreate(req.name(), req.startAt(), req.endAt());

        Campaign c = new Campaign();
        c.setId(UUID.randomUUID().toString());
        c.setSiteId(req.siteId());
        c.setName(req.name());
        c.setStartAt(req.startAt());
        c.setEndAt(req.endAt());
        c.setStatus("planned");
        c.setCreatedAt(Instant.now());
        c.setUpdatedAt(Instant.now());
        campaignMapper.insert(c);
        return CampaignResponse.fromEntity(c);
    }

    @Transactional(rollbackFor = Exception.class)
    public CampaignResponse update(String siteId, String id, CampaignSaveRequest req) {
        Campaign existing = campaignMapper.getByIdAndSiteId(id, siteId);
        if (existing == null) throw BusinessException.campaignNotFound();

        if (req.name() != null) existing.setName(req.name());
        if (req.startAt() != null) existing.setStartAt(req.startAt());
        if (req.endAt() != null) existing.setEndAt(req.endAt());
        // 时间窗校验
        if (existing.getStartAt() != null && existing.getEndAt() != null
                && existing.getEndAt().isBefore(existing.getStartAt())) {
            throw BusinessException.invalidTimeWindow();
        }
        // 状态转换（经聚合根状态机）
        if (req.status() != null && !req.status().equals(existing.getStatus())) {
            CampaignAggregate.transitionCampaign(existing, req.status());
        }
        existing.setUpdatedAt(Instant.now());
        campaignMapper.update(existing);
        return CampaignResponse.fromEntity(existing);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(String siteId, String id) {
        Campaign existing = campaignMapper.getByIdAndSiteId(id, siteId);
        if (existing == null) throw BusinessException.campaignNotFound();
        campaignMapper.deleteById(id);
    }
}

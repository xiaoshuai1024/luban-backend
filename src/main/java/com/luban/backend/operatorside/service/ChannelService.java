package com.luban.backend.operatorside.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luban.backend.shared.domain.CampaignAggregate;
import com.luban.backend.shared.dto.ChannelResponse;
import com.luban.backend.shared.dto.ChannelSaveRequest;
import com.luban.backend.shared.entity.Channel;
import com.luban.backend.shared.entity.Page;
import com.luban.backend.shared.exception.BusinessException;
import com.luban.backend.shared.mapper.ChannelMapper;
import com.luban.backend.shared.mapper.PageMapper;
import com.luban.backend.shared.mapper.SiteMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Channel 渠道/短链领域服务（app-deeplink-backend-arch plan T12）。
 *
 * <p>运营端 CRUD，经 {@link CampaignAggregate} 聚合根校验 invariant。
 * 短码：运营指定 or 系统生成 base62 6 位（plan 决策 2），碰撞重试 3 次。
 * 鉴权：调用方（Controller）经 TenantGuardService.ensureSiteAccess 校验。
 */
@Service
public class ChannelService {

    private static final Logger log = LoggerFactory.getLogger(ChannelService.class);
    private static final int CODE_GEN_MAX_RETRY = 3;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ChannelMapper channelMapper;
    private final SiteMapper siteMapper;
    private final PageMapper pageMapper;

    public ChannelService(ChannelMapper channelMapper, SiteMapper siteMapper, PageMapper pageMapper) {
        this.channelMapper = channelMapper;
        this.siteMapper = siteMapper;
        this.pageMapper = pageMapper;
    }

    public List<ChannelResponse> list(String siteId) {
        if (siteMapper.getById(siteId) == null) throw BusinessException.siteNotFound();
        return channelMapper.listBySiteId(siteId).stream()
                .map(ChannelResponse::fromEntity).collect(Collectors.toList());
    }

    public ChannelResponse get(String siteId, String id) {
        Channel ch = channelMapper.getByIdAndSiteId(id, siteId);
        if (ch == null) throw BusinessException.channelNotFound();
        return ChannelResponse.fromEntity(ch);
    }

    /**
     * 创建 channel。
     * code 缺省则系统生成（碰撞重试 3 次，仍冲突抛 503）。
     */
    @Transactional(rollbackFor = Exception.class)
    public ChannelResponse create(ChannelSaveRequest req) {
        if (siteMapper.getById(req.siteId()) == null) throw BusinessException.siteNotFound();

        // page 归属校验（防开放重定向）
        Page page = pageMapper.getByIdAndSiteId(req.targetPageId(), req.siteId());
        if (page == null) throw BusinessException.pageNotFound();

        // 短码：运营指定 or 系统生成（碰撞重试）
        String code = resolveCodeWithRetry(req.siteId(), req.code(), req.targetPageId(), page.getSiteId());

        String utmJson = toJson(req.utmTemplate());
        Channel ch = CampaignAggregate.newChannel(
                req.siteId(), req.campaignId(), code, req.type(), utmJson, req.targetPageId());
        ch.setCreatedAt(Instant.now());
        ch.setUpdatedAt(Instant.now());

        try {
            channelMapper.insert(ch);
        } catch (DataIntegrityViolationException e) {
            // 运营指定 code 的唯一约束冲突（uk_site_code）
            throw BusinessException.channelCodeDuplicate();
        }
        return ChannelResponse.fromEntity(ch);
    }

    @Transactional(rollbackFor = Exception.class)
    public ChannelResponse update(String siteId, String id, ChannelSaveRequest req) {
        Channel existing = channelMapper.getByIdAndSiteId(id, siteId);
        if (existing == null) throw BusinessException.channelNotFound();

        // 若改 targetPageId，重新校验归属
        if (req.targetPageId() != null && !req.targetPageId().equals(existing.getTargetPageId())) {
            Page page = pageMapper.getByIdAndSiteId(req.targetPageId(), siteId);
            if (page == null) throw BusinessException.pageNotFound();
            existing.setTargetPageId(req.targetPageId());
        }
        if (req.type() != null) existing.setType(req.type());
        if (req.utmTemplate() != null) existing.setUtmTemplate(toJson(req.utmTemplate()));
        if (req.campaignId() != null) existing.setCampaignId(req.campaignId());
        // 状态转换（经聚合根状态机）
        if (req.status() != null && !req.status().equals(existing.getStatus())) {
            CampaignAggregate.transitionChannel(existing, req.status());
        }
        existing.setUpdatedAt(Instant.now());
        channelMapper.update(existing);
        return ChannelResponse.fromEntity(existing);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(String siteId, String id) {
        Channel existing = channelMapper.getByIdAndSiteId(id, siteId);
        if (existing == null) throw BusinessException.channelNotFound();
        channelMapper.deleteById(id);
    }

    /**
     * 短码解析：运营指定 or 系统生成 + 碰撞重试。
     * 运营指定时直接返回（由 DB 唯一约束兜底冲突 → 409）。
     * 系统生成时预检 countBySiteIdAndCode，碰撞重试最多 3 次。
     */
    private String resolveCodeWithRetry(String siteId, String code, String targetPageId, String pageSiteId) {
        if (code != null && !code.isBlank()) {
            // 运营指定：聚合根校验格式 + page 归属
            return CampaignAggregate.validateAndResolveCode(siteId, code, targetPageId, pageSiteId);
        }
        // 系统生成：碰撞重试
        for (int i = 0; i < CODE_GEN_MAX_RETRY; i++) {
            String generated = CampaignAggregate.validateAndResolveCode(siteId, null, targetPageId, pageSiteId);
            if (channelMapper.countBySiteIdAndCode(siteId, generated) == 0) {
                return generated;
            }
            log.debug("短码碰撞重试 {}/{}: {}", i + 1, CODE_GEN_MAX_RETRY, generated);
        }
        throw BusinessException.codeGenFailed();
    }

    private String toJson(com.fasterxml.jackson.databind.JsonNode node) {
        if (node == null) return null;
        try {
            return MAPPER.writeValueAsString(node);
        } catch (Exception e) {
            throw BusinessException.invalidArgument("utmTemplate 序列化失败");
        }
    }
}

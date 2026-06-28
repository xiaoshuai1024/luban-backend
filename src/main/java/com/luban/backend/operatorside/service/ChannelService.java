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
 * 鉴权：每个公开方法入口调 {@link TenantGuardService#ensureSiteAccess} 校验站点归属。
 */
@Service
public class ChannelService {

    private static final Logger log = LoggerFactory.getLogger(ChannelService.class);
    private static final int CODE_GEN_MAX_RETRY = 3;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ChannelMapper channelMapper;
    private final SiteMapper siteMapper;
    private final PageMapper pageMapper;
    private final TenantGuardService tenantGuard;

    public ChannelService(ChannelMapper channelMapper, SiteMapper siteMapper, PageMapper pageMapper,
                          TenantGuardService tenantGuard) {
        this.channelMapper = channelMapper;
        this.siteMapper = siteMapper;
        this.pageMapper = pageMapper;
        this.tenantGuard = tenantGuard;
    }

    /** 站点存在性 + 归属校验（统一入口，避免每个方法重复） */
    private void ensureSite(String siteId) {
        if (siteMapper.getById(siteId) == null) throw BusinessException.siteNotFound();
        tenantGuard.ensureSiteAccess(siteId);
    }

    public List<ChannelResponse> list(String siteId) {
        ensureSite(siteId);
        return channelMapper.listBySiteId(siteId).stream()
                .map(ChannelResponse::fromEntity).collect(Collectors.toList());
    }

    public ChannelResponse get(String siteId, String id) {
        ensureSite(siteId);
        Channel ch = channelMapper.getByIdAndSiteId(id, siteId);
        if (ch == null) throw BusinessException.channelNotFound();
        return ChannelResponse.fromEntity(ch);
    }

    /**
     * 创建 channel。
     * code 缺省则系统生成（碰撞重试 3 次，仍冲突抛 503）。
     * TOCTOU 修复：insert 放进重试循环，DB 唯一约束做最终裁决。
     */
    @Transactional(rollbackFor = Exception.class)
    public ChannelResponse create(ChannelSaveRequest req) {
        ensureSite(req.siteId());

        // page 归属校验（防开放重定向）
        Page page = pageMapper.getByIdAndSiteId(req.targetPageId(), req.siteId());
        if (page == null) throw BusinessException.pageNotFound();

        // 类型枚举校验
        if (!isValidType(req.type())) throw BusinessException.invalidArgument("非法 type: " + req.type());

        String utmJson = toJson(req.utmTemplate());

        // 运营指定 code：单次尝试，DB 唯一约束冲突 → 409
        if (req.code() != null && !req.code().isBlank()) {
            String code = CampaignAggregate.validateAndResolveCode(
                    req.siteId(), req.code(), req.targetPageId(), page.getSiteId());
            return insertChannel(req, code, utmJson);
        }
        // 系统生成：insert 进重试循环，DB 唯一约束做最终裁决（消除 TOCTOU）。
        // 注意：必须直接调 channelMapper.insert（绕过 insertChannel 的 DIVE→409 转换），
        // 否则异常类型被改写，外层 catch(DataIntegrityViolationException) 匹配不到 → 死代码。
        for (int i = 0; i < CODE_GEN_MAX_RETRY; i++) {
            String code = CampaignAggregate.validateAndResolveCode(
                    req.siteId(), null, req.targetPageId(), page.getSiteId());
            Channel ch = CampaignAggregate.newChannel(
                    req.siteId(), req.campaignId(), code, req.type(), utmJson, req.targetPageId());
            ch.setCreatedAt(Instant.now());
            ch.setUpdatedAt(Instant.now());
            try {
                channelMapper.insert(ch);
                return ChannelResponse.fromEntity(ch);
            } catch (DataIntegrityViolationException e) {
                log.debug("短码碰撞重试 {}/{}: {}", i + 1, CODE_GEN_MAX_RETRY, code);
            }
        }
        throw BusinessException.codeGenFailed();
    }

    private ChannelResponse insertChannel(ChannelSaveRequest req, String code, String utmJson) {
        Channel ch = CampaignAggregate.newChannel(
                req.siteId(), req.campaignId(), code, req.type(), utmJson, req.targetPageId());
        ch.setCreatedAt(Instant.now());
        ch.setUpdatedAt(Instant.now());
        try {
            channelMapper.insert(ch);
        } catch (DataIntegrityViolationException e) {
            // 运营指定 code 的唯一约束冲突（uk_site_code / uk_short_url）→ 409
            throw BusinessException.channelCodeDuplicate();
        }
        return ChannelResponse.fromEntity(ch);
    }

    private static final java.util.Set<String> VALID_TYPES = java.util.Set.of(
            CampaignAggregate.ChannelType.QRCODE, CampaignAggregate.ChannelType.H5,
            CampaignAggregate.ChannelType.SOCIAL, CampaignAggregate.ChannelType.AD,
            CampaignAggregate.ChannelType.MINIAPP);

    private static boolean isValidType(String type) {
        return type != null && VALID_TYPES.contains(type);
    }

    @Transactional(rollbackFor = Exception.class)
    public ChannelResponse update(String siteId, String id, ChannelSaveRequest req) {
        ensureSite(siteId);
        Channel existing = channelMapper.getByIdAndSiteId(id, siteId);
        if (existing == null) throw BusinessException.channelNotFound();

        // 若改 targetPageId，重新校验归属
        if (req.targetPageId() != null && !req.targetPageId().equals(existing.getTargetPageId())) {
            Page page = pageMapper.getByIdAndSiteId(req.targetPageId(), siteId);
            if (page == null) throw BusinessException.pageNotFound();
            existing.setTargetPageId(req.targetPageId());
        }
        if (req.type() != null) {
            if (!isValidType(req.type())) throw BusinessException.invalidArgument("非法 type: " + req.type());
            existing.setType(req.type());
        }
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
        ensureSite(siteId);
        Channel existing = channelMapper.getByIdAndSiteId(id, siteId);
        if (existing == null) throw BusinessException.channelNotFound();
        channelMapper.deleteByIdAndSiteId(id, siteId);
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

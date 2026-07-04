package com.luban.backend.shared.repository;

import com.luban.backend.shared.domain.CampaignAggregate;
import com.luban.backend.shared.entity.Campaign;

import java.util.List;
import java.util.Optional;

/**
 * Campaign 活动仓储接口（backend-ddd-refactor plan v2，对齐 FormRepository 范式）。
 *
 * <p>领域抽象。封装 {@code CampaignMapper}（Campaign 聚合）+ 跨聚合查询
 * {@code ChannelMapper.listByCampaignId}（删除前置校验，封装在实现层，接口不暴露 ChannelMapper，
 * CampaignService 零 ChannelMapper 依赖）。
 *
 * @see CampaignAggregate
 */
public interface CampaignRepository {

    /**
     * 按 (id, siteId) 加载聚合根（租户守卫），含聚合内 channels；不存在返回 empty。
     */
    Optional<CampaignAggregate> findById(String id, String siteId);

    /** 列表查询（读模型，按 siteId）。 */
    List<Campaign> listBySiteId(String siteId);

    /** 保存（insert or update）。 */
    void save(CampaignAggregate aggregate);

    /** 按 (id, siteId) 删除。 */
    void deleteByIdAndSiteId(String id, String siteId);

    /**
     * 该 campaign 是否仍挂载 channel（删除前置校验）。
     *
     * <p>跨聚合查询封装在此（实现层调 ChannelMapper），让 CampaignService 零跨聚合 Mapper 依赖。
     * 返回值传给 {@link CampaignAggregate#assertDeletable(boolean)} 做决策断言。
     */
    boolean hasChannels(String campaignId);
}

package com.luban.backend.operatorside.repository;

import com.luban.backend.shared.domain.CampaignAggregate;
import com.luban.backend.shared.entity.Campaign;
import com.luban.backend.shared.mapper.CampaignMapper;
import com.luban.backend.shared.mapper.ChannelMapper;
import com.luban.backend.shared.repository.CampaignRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Campaign 仓储实现（backend-ddd-refactor plan v2，对齐 FormRepositoryImpl 范式）。
 *
 * <p>封装 {@link CampaignMapper} + 跨聚合查询 {@link ChannelMapper#listByCampaignId}（删除前置校验）。
 * ChannelMapper 是实现细节，{@link CampaignRepository} 接口不暴露它，CampaignService 零跨聚合 Mapper 依赖。
 *
 * <p>save 走 insert/update 分流（{@link CampaignMapper#getById} 判存在性），与 FormRepositoryImpl 一致。
 */
@Repository
public class CampaignRepositoryImpl implements CampaignRepository {

    private final CampaignMapper campaignMapper;
    private final ChannelMapper channelMapper;

    public CampaignRepositoryImpl(CampaignMapper campaignMapper, ChannelMapper channelMapper) {
        this.campaignMapper = campaignMapper;
        this.channelMapper = channelMapper;
    }

    @Override
    public Optional<CampaignAggregate> findById(String id, String siteId) {
        Campaign c = campaignMapper.getByIdAndSiteId(id, siteId);
        if (c == null) {
            return Optional.empty();
        }
        List<com.luban.backend.shared.entity.Channel> channels = channelMapper.listByCampaignId(id);
        return Optional.of(CampaignAggregate.reconstitute(c, channels));
    }

    @Override
    public List<Campaign> listBySiteId(String siteId) {
        return campaignMapper.listBySiteId(siteId);
    }

    @Override
    public void save(CampaignAggregate aggregate) {
        Campaign entity = aggregate.toCampaign();
        if (campaignMapper.getById(entity.getId()) == null) {
            campaignMapper.insert(entity);
        } else {
            campaignMapper.update(entity);
        }
    }

    @Override
    public void deleteByIdAndSiteId(String id, String siteId) {
        campaignMapper.deleteByIdAndSiteId(id, siteId);
    }

    @Override
    public boolean hasChannels(String campaignId) {
        return !channelMapper.listByCampaignId(campaignId).isEmpty();
    }
}

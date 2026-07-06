package com.luban.backend.operatorside.repository;

import com.luban.backend.shared.entity.Channel;
import com.luban.backend.shared.mapper.ChannelMapper;
import com.luban.backend.shared.repository.ChannelRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Channel 仓储实现：封装 {@link ChannelMapper}。
 * Channel 为 Campaign 聚合下的子实体（共享表），故直接返回 entity 而非聚合根。
 */
@Repository
public class ChannelRepositoryImpl implements ChannelRepository {

    private final ChannelMapper channelMapper;

    public ChannelRepositoryImpl(ChannelMapper channelMapper) {
        this.channelMapper = channelMapper;
    }

    @Override
    public List<Channel> listBySiteId(String siteId) {
        return channelMapper.listBySiteId(siteId);
    }

    @Override
    public Optional<Channel> getByShortUrl(String shortUrl) {
        return Optional.ofNullable(channelMapper.getByShortUrl(shortUrl));
    }

    @Override
    public Optional<Channel> getByIdAndSiteId(String id, String siteId) {
        return Optional.ofNullable(channelMapper.getByIdAndSiteId(id, siteId));
    }

    @Override
    public List<Channel> listByCampaignId(String campaignId) {
        return channelMapper.listByCampaignId(campaignId);
    }

    @Override
    public boolean existsBySiteIdAndCode(String siteId, String code) {
        return channelMapper.countBySiteIdAndCode(siteId, code) > 0;
    }

    @Override
    public void insert(Channel channel) {
        channelMapper.insert(channel);
    }

    @Override
    public void update(Channel channel) {
        channelMapper.update(channel);
    }

    @Override
    public void deleteByIdAndSiteId(String id, String siteId) {
        channelMapper.deleteByIdAndSiteId(id, siteId);
    }

    @Override
    public void deleteBySiteId(String siteId) {
        channelMapper.deleteBySiteId(siteId);
    }
}

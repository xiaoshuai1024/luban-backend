package com.luban.backend.shared.repository;

import com.luban.backend.shared.entity.Channel;

import java.util.List;
import java.util.Optional;

/**
 * Channel 仓储接口（domain 抽象，不依赖 MyBatis）。
 *
 * <p>聚合内的 channel 实体共享 Channel 表（非独立聚合根），故本仓储直接返回/接收
 * {@link Channel} entity。这与 PageVersion / TemplateVersion 一致——版本/子实体表
 * 不建模为聚合根，但仍需经 Repository 隔离 Mapper。
 */
public interface ChannelRepository {

    List<Channel> listBySiteId(String siteId);

    /** 按 short_url 解析渠道（公开端短链跳转用）。 */
    Optional<Channel> getByShortUrl(String shortUrl);

    Optional<Channel> getByIdAndSiteId(String id, String siteId);

    List<Channel> listByCampaignId(String campaignId);

    /** 同站短码占用校验（uk_site_code 冲突预检）。返回 true 表示已存在。 */
    boolean existsBySiteIdAndCode(String siteId, String code);

    void insert(Channel channel);

    void update(Channel channel);

    void deleteByIdAndSiteId(String id, String siteId);

    /** 站点级联删：清该站所有渠道。 */
    void deleteBySiteId(String siteId);
}

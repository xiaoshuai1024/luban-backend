package com.luban.backend.operatorside.repository;

import com.luban.backend.shared.mapper.CampaignMapper;
import com.luban.backend.shared.mapper.ChannelMapper;
import com.luban.backend.shared.mapper.CollectionMapper;
import com.luban.backend.shared.mapper.DatasourceMapper;
import com.luban.backend.shared.mapper.FormMapper;
import com.luban.backend.shared.mapper.LeadMapper;
import com.luban.backend.shared.mapper.PageMapper;
import org.springframework.stereotype.Component;

/**
 * 站点级联删除持久化协作者（backend-ddd-refactor plan v2 T5 G1 修复）。
 *
 * <p><b>为什么独立于 {@code SiteRepositoryImpl}</b>：Site 聚合根的 Repository 只封装 Site 自身
 * （DDD 边界——一个 Repository 只负责一个聚合）。7 个子表（leads/forms/datasources/collections/
 * channels/campaigns/pages）属于其它聚合，让 SiteRepository 依赖 7 个跨聚合 Mapper 会破坏
 * 聚合边界。本协作者专注"跨表级联删除"这一持久化编排，Application Service 仅委托
 * {@link #deleteChildren(String)}，自身不再持有任何 Mapper。
 *
 * <p>调用方（{@code SiteService.delete}）在 {@code @Transactional} 内调用，本类不做事务声明，
 * 顺序保证由外层事务原子提交。FK 删除顺序：
 * <pre>
 * leads → forms → datasources → collections → channels → campaigns → pages → (site 由 Repository 删)
 * </pre>
 * channels 先于 campaigns（FK 引用 campaigns）；pages 删后 page_versions 经 FK CASCADE 自动清。
 */
@Component
public class SiteCascadeDeleter {

    private final LeadMapper leadMapper;
    private final FormMapper formMapper;
    private final DatasourceMapper datasourceMapper;
    private final CollectionMapper collectionMapper;
    private final ChannelMapper channelMapper;
    private final CampaignMapper campaignMapper;
    private final PageMapper pageMapper;

    public SiteCascadeDeleter(LeadMapper leadMapper, FormMapper formMapper,
                              DatasourceMapper datasourceMapper, CollectionMapper collectionMapper,
                              ChannelMapper channelMapper, CampaignMapper campaignMapper,
                              PageMapper pageMapper) {
        this.leadMapper = leadMapper;
        this.formMapper = formMapper;
        this.datasourceMapper = datasourceMapper;
        this.collectionMapper = collectionMapper;
        this.channelMapper = channelMapper;
        this.campaignMapper = campaignMapper;
        this.pageMapper = pageMapper;
    }

    /**
     * 按 FK 顺序清理站点下所有子表（不含 sites 表本身——sites 由 SiteRepository.deleteById 删）。
     *
     * @param siteId 站点 id
     */
    public void deleteChildren(String siteId) {
        leadMapper.deleteBySiteId(siteId);
        formMapper.deleteBySiteId(siteId);
        datasourceMapper.deleteBySiteId(siteId);
        collectionMapper.deleteBySiteId(siteId);
        channelMapper.deleteBySiteId(siteId);   // channels 先删（FK 引用 campaigns）
        campaignMapper.deleteBySiteId(siteId);  // campaigns 后删
        pageMapper.deleteBySiteId(siteId);      // pages 删后 page_versions 经 FK CASCADE 自动清
    }
}

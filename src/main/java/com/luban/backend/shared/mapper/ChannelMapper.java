package com.luban.backend.shared.mapper;

import com.luban.backend.shared.entity.Channel;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * Channel mapper（app-deeplink-backend-arch plan T10）。
 * 参数化查询防注入；显式列名（禁 SELECT *，对齐既有 mapper 风格）。
 */
@Mapper
public interface ChannelMapper {

    String COLUMNS = "id, site_id, campaign_id, code, type, utm_template, short_url, target_page_id, status, created_at, updated_at";

    @Select("SELECT " + COLUMNS + " FROM channels WHERE site_id = #{siteId} ORDER BY created_at DESC")
    List<Channel> listBySiteId(String siteId);

    @Select("SELECT " + COLUMNS + " FROM channels WHERE id = #{id}")
    Channel getById(String id);

    @Select("SELECT " + COLUMNS + " FROM channels WHERE id = #{id} AND site_id = #{siteId}")
    Channel getByIdAndSiteId(@Param("id") String id, @Param("siteId") String siteId);

    /** 短链解析：按 short_url 查（O(1)，uk_short_url 索引） */
    @Select("SELECT " + COLUMNS + " FROM channels WHERE short_url = #{shortUrl}")
    Channel getByShortUrl(String shortUrl);

    /** campaign 删除前校验：查该 campaign 下的 channel */
    @Select("SELECT " + COLUMNS + " FROM channels WHERE campaign_id = #{campaignId}")
    List<Channel> listByCampaignId(String campaignId);

    /** 校验同站短码是否已存在（uk_site_code 冲突预检） */
    @Select("SELECT COUNT(*) FROM channels WHERE site_id = #{siteId} AND code = #{code}")
    int countBySiteIdAndCode(@Param("siteId") String siteId, @Param("code") String code);

    @Insert("INSERT INTO channels (id, site_id, campaign_id, code, type, utm_template, short_url, target_page_id, status, created_at, updated_at) " +
            "VALUES (#{id}, #{siteId}, #{campaignId}, #{code}, #{type}, #{utmTemplate}, #{shortUrl}, #{targetPageId}, #{status}, #{createdAt}, #{updatedAt})")
    int insert(Channel channel);

    @Update("UPDATE channels SET campaign_id=#{campaignId}, code=#{code}, type=#{type}, utm_template=#{utmTemplate}, short_url=#{shortUrl}, target_page_id=#{targetPageId}, status=#{status}, updated_at=#{updatedAt} WHERE id=#{id} AND site_id=#{siteId}")
    int update(Channel channel);

    @Delete("DELETE FROM channels WHERE id = #{id} AND site_id = #{siteId}")
    int deleteByIdAndSiteId(@Param("id") String id, @Param("siteId") String siteId);

    /** 级联删：删站点时清该站所有渠道（SiteService.delete 调用） */
    @Delete("DELETE FROM channels WHERE site_id = #{siteId}")
    int deleteBySiteId(String siteId);
}

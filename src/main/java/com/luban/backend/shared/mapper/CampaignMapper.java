package com.luban.backend.shared.mapper;

import com.luban.backend.shared.entity.Campaign;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * Campaign mapper（app-deeplink-backend-arch plan T11）。
 * 参数化查询防注入；显式列名。
 */
@Mapper
public interface CampaignMapper {

    String COLUMNS = "id, site_id, name, start_at, end_at, status, created_at, updated_at";

    @Select("SELECT " + COLUMNS + " FROM campaigns WHERE site_id = #{siteId} ORDER BY created_at DESC")
    List<Campaign> listBySiteId(String siteId);

    @Select("SELECT " + COLUMNS + " FROM campaigns WHERE id = #{id}")
    Campaign getById(String id);

    @Select("SELECT " + COLUMNS + " FROM campaigns WHERE id = #{id} AND site_id = #{siteId}")
    Campaign getByIdAndSiteId(@Param("id") String id, @Param("siteId") String siteId);

    @Insert("INSERT INTO campaigns (id, site_id, name, start_at, end_at, status, created_at, updated_at) " +
            "VALUES (#{id}, #{siteId}, #{name}, #{startAt}, #{endAt}, #{status}, #{createdAt}, #{updatedAt})")
    int insert(Campaign campaign);

    @Update("UPDATE campaigns SET name=#{name}, start_at=#{startAt}, end_at=#{endAt}, status=#{status}, updated_at=#{updatedAt} WHERE id=#{id} AND site_id=#{siteId}")
    int update(Campaign campaign);

    @Delete("DELETE FROM campaigns WHERE id = #{id} AND site_id = #{siteId}")
    int deleteByIdAndSiteId(@Param("id") String id, @Param("siteId") String siteId);

    /** 级联删：删站点时清该站所有活动（SiteService.delete 调用） */
    @Delete("DELETE FROM campaigns WHERE site_id = #{siteId}")
    int deleteBySiteId(String siteId);
}

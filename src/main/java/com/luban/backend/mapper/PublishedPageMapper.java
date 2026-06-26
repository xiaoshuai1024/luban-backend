package com.luban.backend.mapper;

import com.luban.backend.entity.PublishedPage;
import org.apache.ibatis.annotations.*;

/**
 * 发布快照 Mapper；表 published_pages。
 *
 * <p>P0 发布闭环：upsert（发布/重新发布）、getBySiteAndPath（公开接口）、
 * deleteByPageId（下线）。
 */
@Mapper
public interface PublishedPageMapper {

    /** 按站点 + 路径查发布快照（公开接口使用，无需认证）。 */
    @Select("SELECT id, page_id, site_id, name, path, schema_json, seo_json, published_at, published_by " +
            "FROM published_pages WHERE site_id = #{siteId} AND path = #{path}")
    PublishedPage getBySiteIdAndPath(@Param("siteId") String siteId, @Param("path") String path);

    /** 发布时 upsert：同 page_id 先删后插（处理重新发布）。 */
    @Delete("DELETE FROM published_pages WHERE page_id = #{pageId}")
    int deleteByPageId(@Param("pageId") String pageId);

    @Insert("INSERT INTO published_pages (id, page_id, site_id, name, path, schema_json, seo_json, published_at, published_by) " +
            "VALUES (#{id}, #{pageId}, #{siteId}, #{name}, #{path}, #{schemaJson}, #{seoJson}, #{publishedAt}, #{publishedBy})")
    int insert(PublishedPage publishedPage);

    /** 下线时按 page_id 删除发布快照。 */
    @Delete("DELETE FROM published_pages WHERE page_id = #{pageId} AND site_id = #{siteId}")
    int deleteByPageIdAndSiteId(@Param("pageId") String pageId, @Param("siteId") String siteId);
}

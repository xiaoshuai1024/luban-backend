package com.luban.backend.mapper;

import com.luban.backend.entity.Page;
import org.apache.ibatis.annotations.*;

import java.time.Instant;
import java.util.List;

/**
 * P0 发布闭环：SELECT 新增 published_at, published_by 列。
 * 公开查询改用 {@link PublishedPageMapper}（读 published_pages 发布快照表）。
 */
@Mapper
public interface PageMapper {

    @Select("SELECT id, site_id, name, path, status, schema_json, seo_json, published_at, published_by, created_at, updated_at FROM pages WHERE site_id = #{siteId} ORDER BY updated_at DESC")
    List<Page> listBySiteId(String siteId);

    @Select("SELECT id, site_id, name, path, status, schema_json, seo_json, published_at, published_by, created_at, updated_at FROM pages WHERE id = #{pageId} AND site_id = #{siteId}")
    Page getByIdAndSiteId(@Param("pageId") String pageId, @Param("siteId") String siteId);

    /** 仅用于状态回填等内部逻辑，公开接口请用 PublishedPageMapper。 */
    @Select("SELECT id, site_id, name, path, status, schema_json, seo_json, published_at, published_by, created_at, updated_at FROM pages WHERE site_id = #{siteId} AND path = #{path} AND status = 'published'")
    Page getBySiteIdAndPathPublished(@Param("siteId") String siteId, @Param("path") String path);

    @Insert("INSERT INTO pages (id, site_id, name, path, status, schema_json, seo_json, created_at, updated_at) " +
            "VALUES (#{id}, #{siteId}, #{name}, #{path}, #{status}, #{schemaJson}, #{seoJson}, #{createdAt}, #{updatedAt})")
    int insert(Page page);

    @Update("UPDATE pages SET name=#{name}, path=#{path}, status=#{status}, schema_json=#{schemaJson}, seo_json=#{seoJson}, updated_at=#{updatedAt} WHERE id=#{id} AND site_id=#{siteId}")
    int update(Page page);

    /** P0 发布闭环：更新发布状态（不改草稿内容）。 */
    @Update("UPDATE pages SET status=#{status}, published_at=#{publishedAt}, published_by=#{publishedBy}, updated_at=#{updatedAt} WHERE id=#{id} AND site_id=#{siteId}")
    int updatePublishStatus(@Param("id") String id, @Param("siteId") String siteId,
                            @Param("status") String status,
                            @Param("publishedAt") Instant publishedAt,
                            @Param("publishedBy") String publishedBy,
                            @Param("updatedAt") Instant updatedAt);

    @Delete("DELETE FROM pages WHERE id = #{pageId} AND site_id = #{siteId}")
    int deleteByIdAndSiteId(@Param("pageId") String pageId, @Param("siteId") String siteId);

    /** V2 级联删除：删站点时先清 pages（page_versions 由 FK CASCADE 自动清） */
    @Delete("DELETE FROM pages WHERE site_id = #{siteId}")
    int deleteBySiteId(@Param("siteId") String siteId);
}

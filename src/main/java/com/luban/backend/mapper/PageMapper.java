package com.luban.backend.mapper;

import com.luban.backend.entity.Page;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface PageMapper {

    @Select("SELECT id, site_id, name, path, status, schema_json, created_at, updated_at FROM pages WHERE site_id = #{siteId} ORDER BY updated_at DESC")
    List<Page> listBySiteId(String siteId);

    @Select("SELECT id, site_id, name, path, status, schema_json, created_at, updated_at FROM pages WHERE id = #{pageId} AND site_id = #{siteId}")
    Page getByIdAndSiteId(@Param("pageId") String pageId, @Param("siteId") String siteId);

    @Select("SELECT id, site_id, name, path, status, schema_json, created_at, updated_at FROM pages WHERE site_id = #{siteId} AND path = #{path} AND status = 'published'")
    Page getBySiteIdAndPathPublished(@Param("siteId") String siteId, @Param("path") String path);

    @Insert("INSERT INTO pages (id, site_id, name, path, status, schema_json, created_at, updated_at) " +
            "VALUES (#{id}, #{siteId}, #{name}, #{path}, #{status}, #{schemaJson}, #{createdAt}, #{updatedAt})")
    int insert(Page page);

    @Update("UPDATE pages SET name=#{name}, path=#{path}, status=#{status}, schema_json=#{schemaJson}, updated_at=#{updatedAt} WHERE id=#{id} AND site_id=#{siteId}")
    int update(Page page);

    @Delete("DELETE FROM pages WHERE id = #{pageId} AND site_id = #{siteId}")
    int deleteByIdAndSiteId(@Param("pageId") String pageId, @Param("siteId") String siteId);
}

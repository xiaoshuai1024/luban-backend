package com.luban.backend.mapper;

import com.luban.backend.entity.PageVersion;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * PageVersion Mapper（MyBatis 注解）：版本列表 / 详情 / 自增插入 / 当前最大版本号。
 */
@Mapper
public interface PageVersionMapper {

    String COLS = "id, site_id, page_id, version, schema_json, operator_id, created_at";

    /** 指定页面的版本列表（按版本号倒序）。 */
    @Select("SELECT " + COLS + " FROM page_versions WHERE page_id = #{pageId} AND site_id = #{siteId} ORDER BY version DESC")
    List<PageVersion> listByPageId(@Param("siteId") String siteId, @Param("pageId") String pageId);

    /** 指定版本的详情。 */
    @Select("SELECT " + COLS + " FROM page_versions WHERE page_id = #{pageId} AND site_id = #{siteId} AND version = #{version}")
    PageVersion getByPageIdAndVersion(@Param("siteId") String siteId, @Param("pageId") String pageId, @Param("version") int version);

    /** 当前页面最大版本号（无版本返回 0）。 */
    @Select("SELECT COALESCE(MAX(version), 0) FROM page_versions WHERE page_id = #{pageId} AND site_id = #{siteId}")
    int maxVersion(@Param("siteId") String siteId, @Param("pageId") String pageId);

    @Insert("INSERT INTO page_versions (id, site_id, page_id, version, schema_json, operator_id, created_at) "
            + "VALUES (#{id}, #{siteId}, #{pageId}, #{version}, #{schemaJson}, #{operatorId}, #{createdAt})")
    int insert(PageVersion pageVersion);
}

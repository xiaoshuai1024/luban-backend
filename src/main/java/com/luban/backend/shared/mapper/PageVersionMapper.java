package com.luban.backend.shared.mapper;

import com.luban.backend.shared.entity.PageVersion;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * V2-T8 PageVersion Mapper。
 * 列名显式列出。list 不含 schema_json（列表轻量），get 含。
 */
@Mapper
public interface PageVersionMapper {

    @Select("SELECT id, page_id, version_no, summary, created_by, created_at FROM page_versions WHERE page_id = #{pageId} ORDER BY version_no DESC")
    List<PageVersion> listByPageId(String pageId);

    @Select("SELECT id, page_id, version_no, schema_json, summary, created_by, created_at FROM page_versions WHERE id = #{versionId} AND page_id = #{pageId}")
    PageVersion getByIdAndPageId(@Param("versionId") String versionId, @Param("pageId") String pageId);

    @Select("SELECT COALESCE(MAX(version_no), 0) FROM page_versions WHERE page_id = #{pageId}")
    int maxVersionNo(String pageId);

    @Insert("INSERT INTO page_versions (id, page_id, version_no, schema_json, summary, created_by, created_at) " +
            "VALUES (#{id}, #{pageId}, #{versionNo}, #{schemaJson}, #{summary}, #{createdBy}, #{createdAt})")
    int insert(PageVersion version);

    @Delete("DELETE FROM page_versions WHERE page_id = #{pageId} AND version_no < #{keepFromVersion}")
    int deleteOlderThan(@Param("pageId") String pageId, @Param("keepFromVersion") int keepFromVersion);
}

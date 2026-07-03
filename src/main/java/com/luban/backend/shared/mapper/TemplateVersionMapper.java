package com.luban.backend.shared.mapper;

import com.luban.backend.shared.entity.TemplateVersion;
import org.apache.ibatis.annotations.*;

/**
 * TemplateVersion mapper（template-marketplace plan）。
 * 模板版本快照，每次发布产生新版本；市场展示最新版。
 */
@Mapper
public interface TemplateVersionMapper {

    String COLUMNS = "id, template_id, version, schema_json, change_note, created_at";

    @Select("SELECT " + COLUMNS + " FROM template_versions WHERE id = #{id}")
    TemplateVersion getById(String id);

    /** 取模板的最新版本 */
    @Select("SELECT " + COLUMNS + " FROM template_versions WHERE template_id = #{templateId} ORDER BY version DESC LIMIT 1")
    TemplateVersion getLatestByTemplateId(String templateId);

    /** 取模板的指定版本 */
    @Select("SELECT " + COLUMNS + " FROM template_versions WHERE template_id = #{templateId} AND version = #{version}")
    TemplateVersion getByTemplateIdAndVersion(@Param("templateId") String templateId, @Param("version") Integer version);

    @Insert("INSERT INTO template_versions (id, template_id, version, schema_json, change_note, created_at) " +
            "VALUES (#{id}, #{templateId}, #{version}, #{schemaJson}, #{changeNote}, #{createdAt})")
    int insert(TemplateVersion v);
}

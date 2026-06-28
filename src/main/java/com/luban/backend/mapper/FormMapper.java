package com.luban.backend.mapper;

import com.luban.backend.entity.Form;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * Form 表单 Mapper（MyBatis 注解风格，对齐 PageMapper）。
 */
@Mapper
public interface FormMapper {

    String COLS = "id, site_id, page_id, name, field_schema_json, submit_config_json, dedup_keys_json, "
            + "dedup_window, dedup_policy, anti_spam_json, status, created_at, updated_at";

    @Select("SELECT " + COLS + " FROM forms WHERE id = #{id}")
    Form getById(@Param("id") String id);

    @Select("SELECT " + COLS + " FROM forms WHERE id = #{id} AND site_id = #{siteId}")
    Form getByIdAndSiteId(@Param("id") String id, @Param("siteId") String siteId);

    @Select("SELECT " + COLS + " FROM forms WHERE site_id = #{siteId} ORDER BY updated_at DESC")
    List<Form> listBySiteId(@Param("siteId") String siteId);

    @Select("SELECT " + COLS + " FROM forms WHERE page_id = #{pageId} ORDER BY updated_at DESC")
    List<Form> listByPageId(@Param("pageId") String pageId);

    @Insert("INSERT INTO forms (id, site_id, page_id, name, field_schema_json, submit_config_json, "
            + "dedup_keys_json, dedup_window, dedup_policy, anti_spam_json, status, created_at, updated_at) "
            + "VALUES (#{id}, #{siteId}, #{pageId}, #{name}, #{fieldSchemaJson}, #{submitConfigJson}, "
            + "#{dedupKeysJson}, #{dedupWindow}, #{dedupPolicy}, #{antiSpamJson}, #{status}, #{createdAt}, #{updatedAt})")
    int insert(Form form);

    @Update("UPDATE forms SET name = #{name}, field_schema_json = #{fieldSchemaJson}, "
            + "submit_config_json = #{submitConfigJson}, dedup_keys_json = #{dedupKeysJson}, "
            + "dedup_window = #{dedupWindow}, dedup_policy = #{dedupPolicy}, anti_spam_json = #{antiSpamJson}, "
            + "status = #{status}, updated_at = #{updatedAt} WHERE id = #{id} AND site_id = #{siteId}")
    int update(Form form);

    /** V2 级联删除：删站点时先清 forms */
    @Delete("DELETE FROM forms WHERE site_id = #{siteId}")
    int deleteBySiteId(@Param("siteId") String siteId);

    /** 删除单个表单（管理端 DELETE，须先校验无线索） */
    @Delete("DELETE FROM forms WHERE id = #{id}")
    int deleteById(@Param("id") String id);
}

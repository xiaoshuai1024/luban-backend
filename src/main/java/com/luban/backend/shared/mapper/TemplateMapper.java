package com.luban.backend.shared.mapper;

import com.luban.backend.shared.entity.Template;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * Template mapper（template-marketplace plan）。
 * 参数化查询防注入；显式列名（禁 SELECT *，对齐既有 mapper 风格）。
 */
@Mapper
public interface TemplateMapper {

    String COLUMNS = "id, slug, name, category, description, thumbnail, author_id, status, latest_version, created_at, updated_at";

    @Select("SELECT " + COLUMNS + " FROM templates WHERE id = #{id}")
    Template getById(String id);

    @Select("SELECT " + COLUMNS + " FROM templates WHERE slug = #{slug}")
    Template getBySlug(String slug);

    /** 市场浏览：published + featured，按 featured 优先、更新时间倒序 */
    @Select("SELECT " + COLUMNS + " FROM templates WHERE status IN ('published','featured') ORDER BY (status='featured') DESC, updated_at DESC")
    List<Template> listMarketplace();

    /** 按类目过滤的市场浏览 */
    @Select("SELECT " + COLUMNS + " FROM templates WHERE status IN ('published','featured') AND category = #{category} ORDER BY (status='featured') DESC, updated_at DESC")
    List<Template> listMarketplaceByCategory(String category);

    /** 运营端全量列表（含 draft/archived） */
    @Select("SELECT " + COLUMNS + " FROM templates ORDER BY updated_at DESC")
    List<Template> listAll();

    @Insert("INSERT INTO templates (id, slug, name, category, description, thumbnail, author_id, status, latest_version, created_at, updated_at) " +
            "VALUES (#{id}, #{slug}, #{name}, #{category}, #{description}, #{thumbnail}, #{authorId}, #{status}, #{latestVersion}, #{createdAt}, #{updatedAt})")
    int insert(Template t);

    @Update("UPDATE templates SET slug=#{slug}, name=#{name}, category=#{category}, description=#{description}, thumbnail=#{thumbnail}, status=#{status}, latest_version=#{latestVersion}, updated_at=#{updatedAt} WHERE id=#{id}")
    int update(Template t);

    @Delete("DELETE FROM templates WHERE id = #{id}")
    int deleteById(String id);

    @Select("SELECT COUNT(*) FROM templates WHERE slug = #{slug}")
    int countBySlug(String slug);
}

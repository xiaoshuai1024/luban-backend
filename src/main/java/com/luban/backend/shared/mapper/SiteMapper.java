package com.luban.backend.shared.mapper;

import com.luban.backend.shared.entity.Site;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface SiteMapper {

    @Select("SELECT id, name, slug, base_url, status, seo_json, analytics_json, created_at, updated_at FROM sites ORDER BY created_at DESC")
    List<Site> list();

    @Select("SELECT id, name, slug, base_url, status, seo_json, analytics_json, created_at, updated_at FROM sites WHERE id = #{id}")
    Site getById(String id);

    @Select("SELECT id, name, slug, base_url, status, seo_json, analytics_json, created_at, updated_at FROM sites WHERE slug = #{slug}")
    Site getBySlug(String slug);

    /** 存在性校验（轻量 COUNT，替代 Service 层 getById 判 null 直连）。 */
    @Select("SELECT COUNT(*) FROM sites WHERE id = #{id}")
    int countById(String id);

    @Select("SELECT COUNT(*) FROM sites WHERE slug = #{slug}")
    int countBySlug(String slug);

    @Insert("INSERT INTO sites (id, name, slug, base_url, status, seo_json, analytics_json, created_at, updated_at) " +
           "VALUES (#{id}, #{name}, #{slug}, #{baseUrl}, #{status}, #{seoJson}, #{analyticsJson}, #{createdAt}, #{updatedAt})")
    int insert(Site site);

    @Update("UPDATE sites SET name=#{name}, slug=#{slug}, base_url=#{baseUrl}, status=#{status}, seo_json=#{seoJson}, analytics_json=#{analyticsJson}, updated_at=#{updatedAt} WHERE id=#{id}")
    int update(Site site);

    @Delete("DELETE FROM sites WHERE id = #{id}")
    int deleteById(String id);
}

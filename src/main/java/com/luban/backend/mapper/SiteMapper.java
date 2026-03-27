package com.luban.backend.mapper;

import com.luban.backend.entity.Site;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface SiteMapper {

    @Select("SELECT id, name, slug, base_url, status, created_at, updated_at FROM sites ORDER BY created_at DESC")
    List<Site> list();

    @Select("SELECT id, name, slug, base_url, status, created_at, updated_at FROM sites WHERE id = #{id}")
    Site getById(String id);

    @Select("SELECT id, name, slug, base_url, status, created_at, updated_at FROM sites WHERE slug = #{slug}")
    Site getBySlug(String slug);

    @Insert("INSERT INTO sites (id, name, slug, base_url, status, created_at, updated_at) " +
           "VALUES (#{id}, #{name}, #{slug}, #{baseUrl}, #{status}, #{createdAt}, #{updatedAt})")
    int insert(Site site);

    @Update("UPDATE sites SET name=#{name}, slug=#{slug}, base_url=#{baseUrl}, status=#{status}, updated_at=#{updatedAt} WHERE id=#{id}")
    int update(Site site);

    @Delete("DELETE FROM sites WHERE id = #{id}")
    int deleteById(String id);
}

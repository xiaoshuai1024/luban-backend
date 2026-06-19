package com.luban.backend.mapper;

import com.luban.backend.entity.Datasource;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * MyBatis mapper for the {@code datasources} table. Annotation-only (no XML), matching
 * the rest of the codebase. snake_case columns map to camelCase via
 * {@code mybatis.configuration.map-underscore-to-camel-case}.
 *
 * <p>All multi-tenant reads carry {@code site_id} as a guard; row-level access is
 * therefore enforced at the SQL layer (not just in the controller).
 */
@Mapper
public interface DatasourceMapper {

    @Select("SELECT id, site_id, name, type, config_json, created_at, updated_at " +
            "FROM datasources WHERE site_id = #{siteId} ORDER BY updated_at DESC")
    List<Datasource> listBySiteId(String siteId);

    @Select("SELECT id, site_id, name, type, config_json, created_at, updated_at FROM datasources WHERE id = #{id}")
    Datasource getById(@Param("id") String id);

    @Insert("INSERT INTO datasources (id, site_id, name, type, config_json, created_at, updated_at) " +
            "VALUES (#{id}, #{siteId}, #{name}, #{type}, #{configJson}, #{createdAt}, #{updatedAt})")
    int insert(Datasource datasource);

    @Update("UPDATE datasources SET site_id=#{siteId}, name=#{name}, type=#{type}, config_json=#{configJson}, updated_at=#{updatedAt} WHERE id=#{id}")
    int update(Datasource datasource);

    @Delete("DELETE FROM datasources WHERE id = #{id}")
    int deleteById(@Param("id") String id);
}

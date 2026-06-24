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
 * therefore enforced at the SQL layer (not just in the controller). The lenient
 * {@code getById} variant is reserved for internal/admin paths where the caller has
 * already established ownership (e.g. {@code testConnection}, which is keyed off id
 * only); tenant-scoped API paths must use {@code getByIdAndSiteId} /
 * {@code deleteByIdAndSiteId} and pass site_id through the {@code update} WHERE.
 */
@Mapper
public interface DatasourceMapper {

    @Select("SELECT id, site_id, name, type, config_json, created_at, updated_at " +
            "FROM datasources WHERE site_id = #{siteId} ORDER BY updated_at DESC")
    List<Datasource> listBySiteId(String siteId);

    /** Internal/admin lookup by id only (no tenant guard). Use sparingly. */
    @Select("SELECT id, site_id, name, type, config_json, created_at, updated_at FROM datasources WHERE id = #{id}")
    Datasource getById(@Param("id") String id);

    /** Tenant-scoped lookup; a wrong-site id is indistinguishable from a missing row. */
    @Select("SELECT id, site_id, name, type, config_json, created_at, updated_at " +
            "FROM datasources WHERE id = #{id} AND site_id = #{siteId}")
    Datasource getByIdAndSiteId(@Param("id") String id, @Param("siteId") String siteId);

    @Insert("INSERT INTO datasources (id, site_id, name, type, config_json, created_at, updated_at) " +
            "VALUES (#{id}, #{siteId}, #{name}, #{type}, #{configJson}, #{createdAt}, #{updatedAt})")
    int insert(Datasource datasource);

    /**
     * Update mutable fields. {@code site_id} is intentionally NOT in the SET clause
     * (a row's tenant never changes) and is added to the WHERE as a multi-tenant guard.
     * 0 rows affected (wrong id OR wrong site_id) → caller treats as NOT_FOUND.
     */
    @Update("UPDATE datasources SET name=#{name}, type=#{type}, config_json=#{configJson}, updated_at=#{updatedAt} " +
            "WHERE id=#{id} AND site_id=#{siteId}")
    int update(Datasource datasource);

    @Delete("DELETE FROM datasources WHERE id = #{id}")
    int deleteById(@Param("id") String id);

    /** Tenant-scoped delete; 0 rows → caller treats as NOT_FOUND. */
    @Delete("DELETE FROM datasources WHERE id = #{id} AND site_id = #{siteId}")
    int deleteByIdAndSiteId(@Param("id") String id, @Param("siteId") String siteId);

    /** V2 级联删除：删站点时先清 datasources */
    @Delete("DELETE FROM datasources WHERE site_id = #{siteId}")
    int deleteBySiteId(@Param("siteId") String siteId);
}

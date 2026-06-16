package com.luban.backend.mapper;

import org.apache.ibatis.annotations.*;

/**
 * user_sites 授权映射 Mapper（🟡 tenant authz）。
 */
@Mapper
public interface UserSiteMapper {

    /** 用户是否有权访问站点（返回 0/1）。 */
    @Select("SELECT COUNT(*) FROM user_sites WHERE user_id = #{userId} AND site_id = #{siteId}")
    int exists(@Param("userId") String userId, @Param("siteId") String siteId);

    /** 授予用户站点访问权。 */
    @Insert("INSERT IGNORE INTO user_sites (user_id, site_id, role, created_at) "
            + "VALUES (#{userId}, #{siteId}, #{role}, NOW(3))")
    int grant(@Param("userId") String userId, @Param("siteId") String siteId, @Param("role") String role);
}

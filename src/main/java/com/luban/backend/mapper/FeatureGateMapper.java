package com.luban.backend.mapper;

import com.luban.backend.entity.FeatureGate;
import org.apache.ibatis.annotations.*;

/**
 * FeatureGate Mapper（MyBatis 注解）：按 siteId+key 读取/写入特性开关。
 * 未配置的开关视为默认开启（plan §3.5），由 service 层兜底。
 */
@Mapper
public interface FeatureGateMapper {

    String COLS = "site_id, gate_key, enabled, updated_at";

    @Select("SELECT " + COLS + " FROM feature_gates WHERE site_id = #{siteId} AND gate_key = #{gateKey}")
    FeatureGate getBySiteAndKey(@Param("siteId") String siteId, @Param("gateKey") String gateKey);

    @Insert("INSERT INTO feature_gates (site_id, gate_key, enabled, updated_at) "
            + "VALUES (#{siteId}, #{gateKey}, #{enabled}, #{updatedAt}) "
            + "ON DUPLICATE KEY UPDATE enabled = #{enabled}, updated_at = #{updatedAt}")
    int upsert(FeatureGate gate);
}

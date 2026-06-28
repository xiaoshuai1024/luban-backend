package com.luban.backend.shared.mapper;

import com.luban.backend.shared.entity.AbExperiment;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * AbExperiment mapper（v02 ab 域）。
 */
@Mapper
public interface AbExperimentMapper {

    String COLS = "id, site_id, page_id, name, status, traffic_pct, start_at, end_at, created_at";

    @Select("SELECT " + COLS + " FROM ab_experiments WHERE id = #{id}")
    AbExperiment getById(@Param("id") String id);

    @Select("<script>" +
            "SELECT " + COLS + " FROM ab_experiments WHERE site_id = #{siteId}" +
            "<if test='status != null and status != \"\"'> AND status = #{status}</if>" +
            " ORDER BY created_at DESC" +
            "</script>")
    List<AbExperiment> listBySite(@Param("siteId") String siteId, @Param("status") String status);

    @Insert("INSERT INTO ab_experiments (" + COLS + ") VALUES (" +
            "#{id}, #{siteId}, #{pageId}, #{name}, #{status}, #{trafficPct}, #{startAt}, #{endAt}, #{createdAt})")
    int insert(AbExperiment experiment);

    @Update("UPDATE ab_experiments SET status = #{status}, start_at = #{startAt}, end_at = #{endAt} WHERE id = #{id}")
    int updateStatus(AbExperiment experiment);

    /** 查某页面是否有 running 实验（单页单 running 约束校验）。 */
    @Select("SELECT " + COLS + " FROM ab_experiments WHERE page_id = #{pageId} AND status = 'running' LIMIT 1")
    AbExperiment findRunningByPage(@Param("pageId") String pageId);
}

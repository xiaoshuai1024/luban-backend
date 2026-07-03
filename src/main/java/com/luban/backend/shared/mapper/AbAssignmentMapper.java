package com.luban.backend.shared.mapper;

import com.luban.backend.shared.entity.AbAssignment;
import org.apache.ibatis.annotations.*;

/**
 * AbAssignment mapper（v02 ab 域）。
 * 一致性哈希分桶：visitor_id + experiment_id 唯一，幂等返回。
 */
@Mapper
public interface AbAssignmentMapper {

    String COLS = "id, visitor_id, experiment_id, variant_id, assigned_at";

    /** 查访客在某实验的分桶（命中则返回，未分桶返回 null）。 */
    @Select("SELECT " + COLS + " FROM ab_assignments WHERE visitor_id = #{visitorId} AND experiment_id = #{experimentId}")
    AbAssignment get(@Param("visitorId") String visitorId, @Param("experimentId") String experimentId);

    @Insert("INSERT IGNORE INTO ab_assignments (" + COLS + ") VALUES " +
            "(#{id}, #{visitorId}, #{experimentId}, #{variantId}, #{assignedAt})")
    int insert(AbAssignment assignment);
}

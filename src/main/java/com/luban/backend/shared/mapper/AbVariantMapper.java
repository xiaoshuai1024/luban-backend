package com.luban.backend.shared.mapper;

import com.luban.backend.shared.entity.AbVariant;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * AbVariant mapper（v02 ab 域）。
 */
@Mapper
public interface AbVariantMapper {

    String COLS = "id, experiment_id, label, page_version_id, weight, is_control";

    @Select("SELECT " + COLS + " FROM ab_variants WHERE experiment_id = #{experimentId} ORDER BY is_control DESC, label ASC")
    List<AbVariant> listByExperiment(@Param("experimentId") String experimentId);

    @Insert("INSERT INTO ab_variants (" + COLS + ") VALUES (" +
            "#{id}, #{experimentId}, #{label}, #{pageVersionId}, #{weight}, #{isControl})")
    int insert(AbVariant variant);

    @Insert("<script>" +
            "INSERT INTO ab_variants (" + COLS + ") VALUES " +
            "<foreach collection='variants' item='v' separator=','>" +
            "(#{v.id}, #{v.experimentId}, #{v.label}, #{v.pageVersionId}, #{v.weight}, #{v.isControl})" +
            "</foreach>" +
            "</script>")
    int batchInsert(@Param("variants") List<AbVariant> variants);
}

package com.luban.backend.mapper;

import com.luban.backend.entity.Plan;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * Plan mapper（v02 billing 域）。
 * 三档套餐由 schema.sql seed 初始化（free/starter/growth）。
 */
@Mapper
public interface PlanMapper {

    String COLS = "plan_code, name, price_monthly, quota_leads, quota_pages, quota_visits, gates, trial_days, sort_order";

    @Select("SELECT " + COLS + " FROM plans ORDER BY sort_order ASC")
    List<Plan> listAll();

    @Select("SELECT " + COLS + " FROM plans WHERE plan_code = #{planCode}")
    Plan getByCode(@Param("planCode") String planCode);
}

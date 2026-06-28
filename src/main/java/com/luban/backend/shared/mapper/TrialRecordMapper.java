package com.luban.backend.shared.mapper;

import com.luban.backend.shared.entity.TrialRecord;
import org.apache.ibatis.annotations.*;

import java.time.Instant;
import java.util.List;

/**
 * TrialRecord mapper（v02 billing 域）。
 * 扫描到期试用：SELECT WHERE ends_at <= now AND converted_to IS NULL。
 */
@Mapper
public interface TrialRecordMapper {

    String COLS = "user_id, trial_plan_code, started_at, ends_at, converted_to";

    @Select("SELECT " + COLS + " FROM trial_records WHERE user_id = #{userId}")
    TrialRecord getByUserId(@Param("userId") String userId);

    @Insert("INSERT INTO trial_records (" + COLS + ") VALUES (#{userId}, #{trialPlanCode}, " +
            "#{startedAt}, #{endsAt}, #{convertedTo})")
    int insert(TrialRecord record);

    @Update("UPDATE trial_records SET converted_to = #{convertedTo} WHERE user_id = #{userId}")
    int markConverted(@Param("userId") String userId, @Param("convertedTo") String convertedTo);

    /** 查询已到期但未降级的试用记录（定时任务用）。 */
    @Select("SELECT " + COLS + " FROM trial_records WHERE ends_at <= #{now} AND converted_to IS NULL")
    List<TrialRecord> listExpiredUnconverted(@Param("now") Instant now);
}

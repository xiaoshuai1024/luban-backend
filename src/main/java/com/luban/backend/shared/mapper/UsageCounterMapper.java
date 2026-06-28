package com.luban.backend.shared.mapper;

import com.luban.backend.shared.entity.UsageCounter;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * UsageCounter mapper（v02 billing 域）。
 * 原子累加：INSERT ... ON DUPLICATE KEY UPDATE count = count + 1（并发安全）。
 */
@Mapper
public interface UsageCounterMapper {

    String COLS = "id, user_id, period_month, metric, count";

    /** 查询用户某月所有 metric 的用量。 */
    @Select("SELECT " + COLS + " FROM usage_counters WHERE user_id = #{userId} AND period_month = #{periodMonth}")
    List<UsageCounter> listByUserPeriod(@Param("userId") String userId, @Param("periodMonth") String periodMonth);

    /**
     * 原子递增用量。利用 UNIQUE KEY uk_usage(user_id, period_month, metric)：
     * 不存在则插入（count=1），存在则 count+1。并发安全。
     */
    @Insert("INSERT INTO usage_counters (id, user_id, period_month, metric, count) " +
            "VALUES (#{id}, #{userId}, #{periodMonth}, #{metric}, 1) " +
            "ON DUPLICATE KEY UPDATE count = count + 1")
    int incrementOrInsert(UsageCounter counter);
}

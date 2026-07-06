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

    /**
     * 原子 check-and-increment（backend-ddd-refactor plan v2 T14，解现状非原子超限问题）：
     * 单条 SQL 同时完成"存在性 upsert + 配额校验 + 递增"。
     *
     * <p>语义：
     * <ul>
     *   <li>行不存在 → 插入 count=1（首条用量，只要 quota &gt;= 1 即通过）</li>
     *   <li>行存在且 count &lt; quota → count+1（递增成功）</li>
     *   <li>行存在且 count &gt;= quota → count 不变（拒绝递增）</li>
     * </ul>
     * 利用 UNIQUE KEY uk_usage(user_id, period_month, metric)，ON DUPLICATE KEY UPDATE 原子执行。
     * 调用方查回 count 与 quota 比较，判定是否超限（而非依赖返回行数——H2/MySQL 返回值语义不一）。
     */
    @Insert("INSERT INTO usage_counters (id, user_id, period_month, metric, count) " +
            "VALUES (#{id}, #{userId}, #{periodMonth}, #{metric}, 1) " +
            "ON DUPLICATE KEY UPDATE count = IF(count < #{quota}, count + 1, count)")
    int incrementAtomicIfUnderQuota(UsageCounter counter,
                                    @Param("quota") int quota);

    /** 查询某用户某月某 metric 的当前 count（check-and-increment 后回读，判定是否超限）。 */
    @Select("SELECT count FROM usage_counters WHERE user_id = #{userId} " +
            "AND period_month = #{periodMonth} AND metric = #{metric}")
    Long getCount(@Param("userId") String userId,
                  @Param("periodMonth") String periodMonth,
                  @Param("metric") String metric);
}

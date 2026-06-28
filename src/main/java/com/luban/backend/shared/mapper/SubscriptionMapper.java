package com.luban.backend.shared.mapper;

import com.luban.backend.shared.entity.Subscription;
import org.apache.ibatis.annotations.*;

/**
 * Subscription mapper（v02 billing 域）。一用户一订阅（user_id PK）。
 */
@Mapper
public interface SubscriptionMapper {

    String COLS = "user_id, plan_code, status, started_at, expires_at, trial_started_at, trial_ends_at";

    @Select("SELECT " + COLS + " FROM subscriptions WHERE user_id = #{userId}")
    Subscription getByUserId(@Param("userId") String userId);

    @Insert("INSERT INTO subscriptions (" + COLS + ") VALUES (#{userId}, #{planCode}, #{status}, " +
            "#{startedAt}, #{expiresAt}, #{trialStartedAt}, #{trialEndsAt})")
    int insert(Subscription subscription);

    @Update("UPDATE subscriptions SET plan_code = #{planCode}, status = #{status}, " +
            "expires_at = #{expiresAt}, trial_started_at = #{trialStartedAt}, trial_ends_at = #{trialEndsAt} " +
            "WHERE user_id = #{userId}")
    int update(Subscription subscription);
}

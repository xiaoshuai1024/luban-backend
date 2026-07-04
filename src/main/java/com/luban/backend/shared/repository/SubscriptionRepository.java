package com.luban.backend.shared.repository;

import com.luban.backend.shared.domain.SubscriptionAggregate;
import com.luban.backend.shared.entity.UsageCounter;

import java.time.Instant;
import java.util.List;

/**
 * 订阅仓储接口（backend-ddd-refactor plan v2 T14）。
 *
 * <p>领域抽象。封装 {@code SubscriptionMapper} + {@code TrialRecordMapper} + {@code UsageCounterMapper}
 * （Subscription 聚合的 3 个领域 Mapper 全部封装，Service 零领域 Mapper 依赖）。
 *
 * @see SubscriptionAggregate
 */
public interface SubscriptionRepository {

    /** 按 userId 加载聚合根（含 TrialRecord，若存在），不存在返回 null。 */
    SubscriptionAggregate findByUserId(String userId);

    /** 保存聚合根（insert or update Subscription，并处理 TrialRecord insert/markConverted）。 */
    void save(SubscriptionAggregate aggregate);

    // === 试用扫描（TrialScheduler 用） ===

    /** 列出已到期未转化的试用记录（ends_at <= now 且 converted_to IS NULL）。 */
    List<com.luban.backend.shared.entity.TrialRecord> listExpiredUnconvertedTrials(Instant now);

    /** 标记试用已转化（无订阅记录的异常兜底，直接标 trial 避免重复扫描）。 */
    void markTrialConverted(String userId, String convertedToPlan);

    // === 用量配额（QuotaService 用，原子操作） ===

    /**
     * 原子 check-and-increment 用量（单条 SQL，并发安全）。
     *
     * @param userId    用户 id
     * @param metric    leads/pages/visits
     * @param quota     配额上限（quota<=0 时按 MAX_VALUE 处理即无限制）
     * @return 递增后的当前 count（调用方据此判定是否超限）
     */
    long incrementUsageAtomicIfUnderQuota(String userId, String metric, int quota);

    /** 查询用户某月所有 metric 的用量（getMyPlan 读模型用）。 */
    List<UsageCounter> listUsageByUserPeriod(String userId, String periodMonth);
}

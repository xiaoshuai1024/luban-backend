package com.luban.backend.operatorside.repository;

import com.luban.backend.shared.domain.SubscriptionAggregate;
import com.luban.backend.shared.entity.Subscription;
import com.luban.backend.shared.entity.UsageCounter;
import com.luban.backend.shared.mapper.SubscriptionMapper;
import com.luban.backend.shared.mapper.TrialRecordMapper;
import com.luban.backend.shared.mapper.UsageCounterMapper;
import com.luban.backend.shared.repository.SubscriptionRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * 订阅仓储实现（backend-ddd-refactor plan v2 T14）。
 *
 * <p>封装 {@link SubscriptionMapper} + {@link TrialRecordMapper} + {@link UsageCounterMapper}
 * （Subscription 聚合的 3 个领域 Mapper 全部封装，Service 零领域 Mapper 依赖）。
 */
@Repository
public class SubscriptionRepositoryImpl implements SubscriptionRepository {

    private final SubscriptionMapper subscriptionMapper;
    private final TrialRecordMapper trialRecordMapper;
    private final UsageCounterMapper usageCounterMapper;

    public SubscriptionRepositoryImpl(SubscriptionMapper subscriptionMapper,
                                      TrialRecordMapper trialRecordMapper,
                                      UsageCounterMapper usageCounterMapper) {
        this.subscriptionMapper = subscriptionMapper;
        this.trialRecordMapper = trialRecordMapper;
        this.usageCounterMapper = usageCounterMapper;
    }

    @Override
    public SubscriptionAggregate findByUserId(String userId) {
        Subscription sub = subscriptionMapper.getByUserId(userId);
        if (sub == null) return null;
        var trial = trialRecordMapper.getByUserId(userId);
        return SubscriptionAggregate.reconstitute(sub, trial);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public void save(SubscriptionAggregate aggregate) {
        Subscription entity = aggregate.toSubscription();
        if (subscriptionMapper.getByUserId(entity.getUserId()) == null) {
            subscriptionMapper.insert(entity);
        } else {
            subscriptionMapper.update(entity);
        }
        var trial = aggregate.toTrialRecord();
        if (trial != null) {
            if (aggregate.isTrialNewlyCreated()) {
                trialRecordMapper.insert(trial);
            } else if (trial.getConvertedTo() != null) {
                trialRecordMapper.markConverted(trial.getUserId(), trial.getConvertedTo());
            }
        }
    }

    @Override
    public List<com.luban.backend.shared.entity.TrialRecord> listExpiredUnconvertedTrials(Instant now) {
        return trialRecordMapper.listExpiredUnconverted(now);
    }

    @Override
    public void markTrialConverted(String userId, String convertedToPlan) {
        trialRecordMapper.markConverted(userId, convertedToPlan);
    }

    @Override
    public long incrementUsageAtomicIfUnderQuota(String userId, String metric, int quota) {
        String periodMonth = LocalDate.now(ZoneOffset.UTC).toString().substring(0, 7);
        int effectiveQuota = quota <= 0 ? Integer.MAX_VALUE : quota;
        UsageCounter counter = new UsageCounter();
        counter.setId(UUID.randomUUID().toString());
        counter.setUserId(userId);
        counter.setPeriodMonth(periodMonth);
        counter.setMetric(metric);
        // 原子：INSERT...ON DUPLICATE KEY UPDATE count=IF(count<quota, count+1, count)
        usageCounterMapper.incrementAtomicIfUnderQuota(counter, effectiveQuota);
        Long current = usageCounterMapper.getCount(userId, periodMonth, metric);
        return current != null ? current : 0L;
    }

    @Override
    public List<UsageCounter> listUsageByUserPeriod(String userId, String periodMonth) {
        return usageCounterMapper.listByUserPeriod(userId, periodMonth);
    }
}

package com.luban.backend.operatorside.service;

import com.luban.backend.shared.domain.SubscriptionAggregate;
import com.luban.backend.shared.repository.SubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 试用降级应用服务（backend-ddd-refactor plan v2 T14，v02 billing 域）。
 *
 * <p>扫描到期试用（ends_at <= now 且 converted_to IS NULL），
 * 经 {@link SubscriptionAggregate#expireTrial()} 完成降级（trialing→active(free)）。
 *
 * <p>被 TrialScheduler（@Scheduled）定时调用；也可手动触发。
 * 降级逻辑（状态机 + 字段清理 + 事件）在聚合根，本服务仅编排：扫描→加载→降级→保存→发布事件。
 * 持久化经 {@link SubscriptionRepository}（不直接依赖领域 Mapper）。
 */
@Service
public class TrialService {

    private static final Logger log = LoggerFactory.getLogger(TrialService.class);

    private final SubscriptionRepository subscriptionRepository;
    private final ApplicationEventPublisher eventPublisher;

    public TrialService(SubscriptionRepository subscriptionRepository,
                        ApplicationEventPublisher eventPublisher) {
        this.subscriptionRepository = subscriptionRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 处理所有到期试用：逐个降级。
     * @return 降级的用户数
     */
    @Transactional(rollbackFor = Exception.class)
    public int expireTrials() {
        Instant now = Instant.now();
        var expired = subscriptionRepository.listExpiredUnconvertedTrials(now);
        int count = 0;
        for (var trial : expired) {
            try {
                downgrade(trial.getUserId());
                count++;
            } catch (Exception e) {
                log.error("试用降级失败 userId={}: {}", trial.getUserId(), e.getMessage());
            }
        }
        if (count > 0) {
            log.info("试用降级完成：{} 个用户从 trialing 降为 free", count);
        }
        return count;
    }

    /** 单个用户降级：经聚合根 expireTrial（状态机 + 字段 + 事件）。 */
    private void downgrade(String userId) {
        SubscriptionAggregate agg = subscriptionRepository.findByUserId(userId);
        if (agg == null) {
            subscriptionRepository.markTrialConverted(userId, "free");
            return;
        }
        agg.expireTrial();
        subscriptionRepository.save(agg);
        agg.pullEvents().forEach(eventPublisher::publishEvent);
    }
}

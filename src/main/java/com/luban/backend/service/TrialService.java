package com.luban.backend.service;

import com.luban.backend.entity.Subscription;
import com.luban.backend.entity.TrialRecord;
import com.luban.backend.mapper.SubscriptionMapper;
import com.luban.backend.mapper.TrialRecordMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 试用降级服务（v02 billing 域，T-be-5）。
 *
 * 扫描到期试用（ends_at <= now 且 converted_to IS NULL），
 * 将 Subscription 从 trialing 降为 active(free)（数据保留，仅降配额）。
 *
 * 被 TrialScheduler（@Scheduled）定时调用；也可手动触发。
 */
@Service
public class TrialService {

    private static final Logger log = LoggerFactory.getLogger(TrialService.class);
    private static final String FREE_PLAN = "free";

    private final TrialRecordMapper trialRecordMapper;
    private final SubscriptionMapper subscriptionMapper;

    public TrialService(TrialRecordMapper trialRecordMapper, SubscriptionMapper subscriptionMapper) {
        this.trialRecordMapper = trialRecordMapper;
        this.subscriptionMapper = subscriptionMapper;
    }

    /**
     * 处理所有到期试用：逐条降级。
     * @return 降级的用户数
     */
    @Transactional(rollbackFor = Exception.class)
    public int expireTrials() {
        Instant now = Instant.now();
        List<TrialRecord> expired = trialRecordMapper.listExpiredUnconverted(now);
        int count = 0;
        for (TrialRecord trial : expired) {
            try {
                downgrade(trial, now);
                count++;
            } catch (Exception e) {
                // 单条失败不阻断整体（已 @Transactional 会回滚单条；此处 try-catch 保护并发场景）
                log.error("试用降级失败 userId={}: {}", trial.getUserId(), e.getMessage());
            }
        }
        if (count > 0) {
            log.info("试用降级完成：{} 个用户从 trialing 降为 free", count);
        }
        return count;
    }

    /** 单个用户降级：Subscription plan→free, status→active；TrialRecord 标记 converted_to。 */
    private void downgrade(TrialRecord trial, Instant now) {
        Subscription sub = subscriptionMapper.getByUserId(trial.getUserId());
        if (sub == null) {
            // 无订阅记录（异常情况），仅标记试用记录避免重复扫描
            trialRecordMapper.markConverted(trial.getUserId(), FREE_PLAN);
            return;
        }
        sub.setPlanCode(FREE_PLAN);
        sub.setStatus("active");
        sub.setTrialEndsAt(now);
        subscriptionMapper.update(sub);
        trialRecordMapper.markConverted(trial.getUserId(), FREE_PLAN);
    }
}

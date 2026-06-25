package com.luban.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 试用降级定时任务（v02 billing 域，T-be-5）。
 *
 * 每小时扫描到期试用（ends_at <= now 且 converted_to IS NULL），
 * 调 TrialService.expireTrials() 将 Subscription 从 trialing 降为 active(free)。
 *
 * 状态机（方案 §3.2）：trialing →（到期）→ active(free)，数据保留，仅降配额。
 */
@Component
public class TrialScheduler {

    private static final Logger log = LoggerFactory.getLogger(TrialScheduler.class);

    private final TrialService trialService;

    public TrialScheduler(TrialService trialService) {
        this.trialService = trialService;
    }

    /** 每小时整点执行（cron: 秒 分 时 日 月 周）。首次延迟 5 分钟避免启动尖峰。 */
    @Scheduled(cron = "0 0 * * * *")
    public void expireTrials() {
        try {
            int count = trialService.expireTrials();
            if (count > 0) {
                log.info("[TrialScheduler] 本轮降级 {} 个到期试用用户", count);
            }
        } catch (Exception e) {
            log.error("[TrialScheduler] 试用降级定时任务异常: {}", e.getMessage(), e);
        }
    }
}

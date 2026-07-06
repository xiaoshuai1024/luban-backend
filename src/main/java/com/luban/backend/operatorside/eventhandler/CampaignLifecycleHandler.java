package com.luban.backend.operatorside.eventhandler;

import com.luban.backend.shared.domain.event.CampaignActivatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 活动生命周期事件处理器（backend-ddd-refactor plan v2 T4 / G1 补）。
 *
 * <p>消费 {@link CampaignActivatedEvent}（覆盖 planned→active / active→completed / →cancelled）。
 * 当前为日志占位（可观测），让活动状态转换进入事件总线；未来真实消费者
 * （渠道下线、analytics 归因窗口启停、订阅计费）接入即可。
 *
 * <p>AFTER_COMMIT + @Async：活动状态已持久化后才发副作用，主事务不被副作用失败阻塞；
 * 异步执行与其他领域事件 handler 一致（domainEventExecutor 线程池）。
 */
@Component
public class CampaignLifecycleHandler {

    private static final Logger log = LoggerFactory.getLogger(CampaignLifecycleHandler.class);

    @Async("domainEventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onActivated(CampaignActivatedEvent event) {
        log.info("Campaign transition: campaignId={}, siteId={}, {} -> {}",
                event.campaignId(), event.siteId(), event.fromStatus(), event.toStatus());
    }
}

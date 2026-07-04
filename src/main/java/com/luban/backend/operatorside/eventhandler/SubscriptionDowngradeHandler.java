package com.luban.backend.operatorside.eventhandler;

import com.luban.backend.shared.domain.event.SubscriptionExpiredEvent;
import com.luban.backend.shared.domain.event.SubscriptionUpgradedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 订阅变更副作用处理器（backend-ddd-refactor plan v2 T4）。
 *
 * <p>消费 {@link SubscriptionExpiredEvent} / {@link SubscriptionUpgradedEvent}，
 * 触发套餐变更后的副作用（FeatureGate 缓存刷新、用量配额重置、用户通知等）。
 *
 * <p><b>职责边界</b>（对齐工程原则，禁越界预实现）：
 * <ul>
 *   <li>订阅的状态变更与持久化由 {@code SubscriptionAggregate}（T14）在聚合根方法内完成，
 *       事件在事务提交后（AFTER_COMMIT）由本 handler 接收</li>
 *   <li>本 handler 只做"变更已落库"后的副作用，<b>不</b>重复任何写库逻辑</li>
 *   <li>T14 SubscriptionAggregate 就位前，副作用以日志占位，保证事件链路完整可测；
 *       配额重置/gate 刷新等具体副作用随 T14 落地补全（避免预实现未设计的聚合行为）</li>
 * </ul>
 *
 * <p>AFTER_COMMIT + @Async：不阻塞订阅变更主事务，失败仅记日志不影响主流程。
 */
@Component
public class SubscriptionDowngradeHandler {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionDowngradeHandler.class);

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(SubscriptionExpiredEvent event) {
        // 订阅/试用到期降级（trialing/active → free）落库后触发。
        // TODO(T14): FeatureGate 缓存失效（owner 的 plan 已变，site 级 gate 判定需重算）
        //           + 用量配额重置为 free 档 + 到期通知推送
        log.info("Subscription expired: userId={}, {} -> {}",
                event.userId(), event.fromPlan(), event.toPlan());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(SubscriptionUpgradedEvent event) {
        // 套餐升级落库后触发（Plan B 付费续费场景）。
        // TODO(T14): FeatureGate 缓存失效 + 新套餐配额生效 + 升级通知
        log.info("Subscription upgraded: userId={}, {} -> {}",
                event.userId(), event.fromPlan(), event.toPlan());
    }
}

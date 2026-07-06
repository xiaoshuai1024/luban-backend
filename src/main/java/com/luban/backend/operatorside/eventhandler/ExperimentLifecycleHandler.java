package com.luban.backend.operatorside.eventhandler;

import com.luban.backend.shared.domain.event.ExperimentEndedEvent;
import com.luban.backend.shared.domain.event.ExperimentStartedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 实验生命周期事件处理器（backend-ddd-refactor plan v2 T4 / G1 补）。
 *
 * <p>消费 {@link ExperimentStartedEvent} / {@link ExperimentEndedEvent}。当前为日志占位（可观测），
 * 让实验启停事件进入事件总线；未来真实消费者（analytics 归因窗口启停、bucketing 冻结、
 * lead-capture 实验结束冻结分桶）接入即可，无需改聚合根或 Service。
 *
 * <p>AFTER_COMMIT + @Async：实验状态已持久化后才发副作用，主事务不被副作用失败阻塞；
 * 异步执行与其他领域事件 handler 一致（domainEventExecutor 线程池）。
 */
@Component
public class ExperimentLifecycleHandler {

    private static final Logger log = LoggerFactory.getLogger(ExperimentLifecycleHandler.class);

    @Async("domainEventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStarted(ExperimentStartedEvent event) {
        log.info("Experiment started: experimentId={}, siteId={}, pageId={}",
                event.experimentId(), event.siteId(), event.pageId());
    }

    @Async("domainEventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEnded(ExperimentEndedEvent event) {
        log.info("Experiment ended: experimentId={}, siteId={}, pageId={}",
                event.experimentId(), event.siteId(), event.pageId());
    }
}

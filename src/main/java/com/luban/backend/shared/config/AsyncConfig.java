package com.luban.backend.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 启用方法级异步（@Async）。为领域事件 handler（留资通知/转化归因/订阅降级）等 AFTER_COMMIT
 * 非阻塞副作用提供线程池。独立线程池避免与 afterCommit 回调线程竞争或阻塞。
 *
 * <p>G1 修复：bean 名从 leadNotifyExecutor 改为 domainEventExecutor（实际服务所有领域事件 handler，
 * 非"仅留资通知"）。handler 通过 @Async("domainEventExecutor") 显式指定使用此池。
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 领域事件异步执行器（@Async("domainEventExecutor")）。
     * 配置偏保守：核心 2、最大 8、队列 100，足以支撑领域事件量级。
     */
    @org.springframework.context.annotation.Bean("domainEventExecutor")
    public Executor domainEventExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("domain-event-");
        // 队列满时由调用线程执行（afterCommit 线程），宁可同步投递也不丢事件。
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}

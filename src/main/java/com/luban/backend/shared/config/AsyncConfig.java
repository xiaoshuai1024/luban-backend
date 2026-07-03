package com.luban.backend.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 启用方法级异步（@Async）。为留资通知 Webhook 投递等非阻塞副作用提供线程池。
 * 留资通知走独立线程池，避免与 afterCommit 的回调线程竞争或阻塞。
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 默认 TaskExecutor（Spring 自动用于 @Async）。命名 "leadNotifyExecutor" 便于 @Async 指定。
     * 配置偏保守：核心 2、最大 8、队列 100，足以支撑留资通知量级。
     */
    @org.springframework.context.annotation.Bean("leadNotifyExecutor")
    public Executor leadNotifyExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("lead-notify-");
        // 队列满时由调用线程执行（afterCommit 线程），宁可同步投递也不丢通知。
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}

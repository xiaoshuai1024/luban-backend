package com.luban.backend.config;

import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * 留资通知相关 Bean 配置。
 * 将 RestClient 提取为可注入 Bean，便于：
 *  1) 集中超时配置（生产单例，避免每次通知新建客户端）；
 *  2) 单测注入 mock RestClient（DefaultLeadNotifyService 无需多构造器）。
 */
@Configuration
public class LeadNotifyConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);

    /**
     * 留资通知专用 RestClient（3s 连接/读取超时）。
     * 命名 "leadNotifyRestClient" 以便多 RestClient 场景下精确注入。
     */
    @Bean("leadNotifyRestClient")
    public RestClient leadNotifyRestClient() {
        return RestClient.builder()
                .requestFactory(ClientHttpRequestFactories.get(
                        ClientHttpRequestFactorySettings.DEFAULTS
                                .withConnectTimeout(CONNECT_TIMEOUT)
                                .withReadTimeout(CONNECT_TIMEOUT)))
                .build();
    }
}

package com.luban.backend.operatorside.eventhandler;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.luban.backend.shared.domain.event.SubscriptionExpiredEvent;
import com.luban.backend.shared.domain.event.SubscriptionUpgradedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SubscriptionDowngradeHandler 单测（backend-ddd-refactor plan v2 T16，G1 加强版）。
 *
 * <p>当前 handler 为日志占位（T14 聚合根副作用待补），G1 加强：用 ListAppender 捕获日志，
 * 断言事件字段被正确消费（userId/fromPlan/toPlan），不再用 doesNotThrowAnyException 占位（false-green）。
 *
 * <p>随 T14/T16 落地（FeatureGate 缓存失效/配额重置/通知），在此替换为对真实副作用的断言。
 */
class SubscriptionDowngradeHandlerTest {

    private final SubscriptionDowngradeHandler handler = new SubscriptionDowngradeHandler();
    private ListAppender<ILoggingEvent> appender;
    private Logger logger;

    @BeforeEach
    void attachAppender() {
        logger = (Logger) LoggerFactory.getLogger(SubscriptionDowngradeHandler.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);
    }

    @AfterEach
    void detachAppender() {
        if (logger != null && appender != null) {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private String formattedJoin() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                .reduce("", (a, b) -> a.isEmpty() ? b : a + "\n" + b);
    }

    @Test
    void on_subscriptionExpiredEvent_consumes_and_logs_event_fields() {
        SubscriptionExpiredEvent event = new SubscriptionExpiredEvent(
                "user-1", "pro", "free", Instant.now());

        handler.on(event);

        String log = formattedJoin();
        assertThat(log).contains("userId=user-1", "pro -> free");
    }

    @Test
    void on_subscriptionUpgradedEvent_consumes_and_logs_event_fields() {
        SubscriptionUpgradedEvent event = new SubscriptionUpgradedEvent(
                "user-1", "free", "pro", Instant.now());

        handler.on(event);

        String log = formattedJoin();
        assertThat(log).contains("userId=user-1", "free -> pro");
    }

    @Test
    void on_subscriptionExpiredEvent_handles_null_fromPlan_without_npe() {
        // fromPlan 理论上非 null（expire 路径），但日志消费应容忍边界，且仍记录 userId/toPlan
        SubscriptionExpiredEvent event = new SubscriptionExpiredEvent(
                "user-1", null, "free", Instant.now());

        handler.on(event);

        String log = formattedJoin();
        assertThat(log).contains("userId=user-1", "free");
    }
}

package com.luban.backend.operatorside.eventhandler;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.luban.backend.shared.domain.event.CampaignActivatedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CampaignLifecycleHandler 单测（G1 补：状态机事件 handler）。
 *
 * <p>当前 handler 为日志占位，用 ListAppender 捕获日志断言事件字段
 *（campaignId/siteId + from→to 转换），证明事件被消费而非 no-op。
 */
class CampaignLifecycleHandlerTest {

    private final CampaignLifecycleHandler handler = new CampaignLifecycleHandler();
    private ListAppender<ILoggingEvent> appender;
    private Logger logger;

    @BeforeEach
    void attachAppender() {
        logger = (Logger) LoggerFactory.getLogger(CampaignLifecycleHandler.class);
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
    void onActivated_plannedToActive_logs_transition() {
        CampaignActivatedEvent event = new CampaignActivatedEvent(
                "camp-1", "site-1", "planned", "active", Instant.now());

        handler.onActivated(event);

        assertThat(formattedJoin()).contains("campaignId=camp-1", "siteId=site-1", "planned -> active");
    }

    @Test
    void onActivated_activeToCompleted_logs_transition() {
        CampaignActivatedEvent event = new CampaignActivatedEvent(
                "camp-1", "site-1", "active", "completed", Instant.now());

        handler.onActivated(event);

        assertThat(formattedJoin()).contains("campaignId=camp-1", "active -> completed");
    }

    @Test
    void onActivated_activeToCancelled_logs_transition() {
        CampaignActivatedEvent event = new CampaignActivatedEvent(
                "camp-1", "site-1", "active", "cancelled", Instant.now());

        handler.onActivated(event);

        assertThat(formattedJoin()).contains("campaignId=camp-1", "active -> cancelled");
    }
}

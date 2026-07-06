package com.luban.backend.operatorside.eventhandler;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.luban.backend.shared.domain.event.ExperimentEndedEvent;
import com.luban.backend.shared.domain.event.ExperimentStartedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ExperimentLifecycleHandler 单测（G1 补：状态机事件 handler）。
 *
 * <p>当前 handler 为日志占位，用 ListAppender 捕获日志断言事件字段（experimentId/siteId/pageId），
 * 证明事件被消费而非 no-op。
 */
class ExperimentLifecycleHandlerTest {

    private final ExperimentLifecycleHandler handler = new ExperimentLifecycleHandler();
    private ListAppender<ILoggingEvent> appender;
    private Logger logger;

    @BeforeEach
    void attachAppender() {
        logger = (Logger) LoggerFactory.getLogger(ExperimentLifecycleHandler.class);
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
    void onStarted_consumes_and_logs_event_fields() {
        ExperimentStartedEvent event = new ExperimentStartedEvent(
                "exp-1", "site-1", "page-1", Instant.now());

        handler.onStarted(event);

        assertThat(formattedJoin()).contains("experimentId=exp-1", "siteId=site-1", "pageId=page-1");
    }

    @Test
    void onEnded_consumes_and_logs_event_fields() {
        ExperimentEndedEvent event = new ExperimentEndedEvent(
                "exp-1", "site-1", "page-1", Instant.now());

        handler.onEnded(event);

        assertThat(formattedJoin()).contains("experimentId=exp-1", "siteId=site-1", "pageId=page-1");
    }
}

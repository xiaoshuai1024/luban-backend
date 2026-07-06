package com.luban.backend.operatorside.eventhandler;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.luban.backend.shared.domain.event.PagePublishedEvent;
import com.luban.backend.shared.domain.event.PageUnpublishedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PagePublishSideEffectHandler 单测（backend-ddd-refactor plan v2 T16，G1 加强版）。
 *
 * <p>当前 handler 为日志占位（副作用基础设施 P1 落地），G1 加强：用 ListAppender 捕获日志，
 * 断言事件字段被正确消费（siteId/pageId/path），不再用 doesNotThrowAnyException 占位（false-green）。
 *
 * <p>随 P1 副作用落地（短链刷新/SEO 缓存失效），在此替换为对真实副作用的断言。
 */
class PagePublishSideEffectHandlerTest {

    private final PagePublishSideEffectHandler handler = new PagePublishSideEffectHandler();
    private ListAppender<ILoggingEvent> appender;
    private Logger logger;

    @BeforeEach
    void attachAppender() {
        logger = (Logger) LoggerFactory.getLogger(PagePublishSideEffectHandler.class);
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
    void on_pagePublishedEvent_consumes_and_logs_event_fields() {
        PagePublishedEvent event = new PagePublishedEvent(
                "page-1", "site-1", "/landing", Instant.now());

        handler.on(event);

        // 断言日志含事件关键字段（siteId/pageId/path），证明事件被消费而非 no-op
        String log = formattedJoin();
        assertThat(log).contains("siteId=site-1", "pageId=page-1", "path=/landing");
    }

    @Test
    void on_pageUnpublishedEvent_consumes_and_logs_event_fields() {
        PageUnpublishedEvent event = new PageUnpublishedEvent(
                "page-1", "site-1", Instant.now());

        handler.on(event);

        String log = formattedJoin();
        assertThat(log).contains("siteId=site-1", "pageId=page-1");
    }

    @Test
    void on_pagePublishedEvent_handles_null_path_without_npe() {
        // path 可能为 null（页面无 path 的边界），日志消费不应 NPE，且仍记录 siteId/pageId
        PagePublishedEvent event = new PagePublishedEvent(
                "page-1", "site-1", null, Instant.now());

        handler.on(event);

        String log = formattedJoin();
        assertThat(log).contains("siteId=site-1", "pageId=page-1");
    }
}

package com.luban.backend.operatorside.eventhandler;

import com.luban.backend.shared.domain.event.PagePublishedEvent;
import com.luban.backend.shared.domain.event.PageUnpublishedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 页面发布副作用处理器（backend-ddd-refactor plan v2 T4）。
 *
 * <p>消费 {@link PagePublishedEvent} / {@link PageUnpublishedEvent}，
 * 触发短链刷新 / SEO 缓存失效等副作用。
 *
 * <p>当前为日志占位（副作用基础设施后续按需扩展），保证事件链路完整可测。
 */
@Component
public class PagePublishSideEffectHandler {

    private static final Logger log = LoggerFactory.getLogger(PagePublishSideEffectHandler.class);

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(PagePublishedEvent event) {
        log.info("Page published side-effect: siteId={}, pageId={}, path={}",
                event.siteId(), event.pageId(), event.path());
        // TODO(P1): 短链刷新 / SEO 缓存失效 / sitemap 更新
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(PageUnpublishedEvent event) {
        log.info("Page unpublished side-effect: siteId={}, pageId={}",
                event.siteId(), event.pageId());
        // TODO(P1): published_pages 缓存清理 / 404 兜底
    }
}

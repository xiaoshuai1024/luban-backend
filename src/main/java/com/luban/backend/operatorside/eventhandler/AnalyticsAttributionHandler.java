package com.luban.backend.operatorside.eventhandler;

import com.luban.backend.shared.domain.event.LeadConvertedEvent;
import com.luban.backend.shared.dto.AnalyticsEventInput;
import com.luban.backend.shared.port.AnalyticsIngestPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 线索转化归因处理器（backend-ddd-refactor plan v2 T4）。
 *
 * <p>消费 {@link LeadConvertedEvent}，向 Analytics 发送转化事件用于归因统计。
 * AFTER_COMMIT + @Async：不阻塞主事务。
 *
 * <p><b>幂等（at-least-once 投递）</b>：outbox relay 可能重投。用 leadId 去重——
 * 同一 lead 的转化事件只记一次（内存 Set，进程级）。
 */
@Component
public class AnalyticsAttributionHandler {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsAttributionHandler.class);

    private final AnalyticsIngestPort analyticsIngestPort;
    /** 已归因 leadId 集合（幂等去重，防 relay 重投导致重复计数）。 */
    private final Set<String> attributedLeadIds = ConcurrentHashMap.newKeySet();

    public AnalyticsAttributionHandler(AnalyticsIngestPort analyticsIngestPort) {
        this.analyticsIngestPort = analyticsIngestPort;
    }

    /** 即时投递（主事务提交后）。 */
    @Async("domainEventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(LeadConvertedEvent event) {
        handle(event);
    }

    /** 补偿投递（outbox relay 重发，无事务上下文）。幂等：重复事件被去重。 */
    @EventListener
    public void onRelay(LeadConvertedEvent event) {
        handle(event);
    }

    private void handle(LeadConvertedEvent event) {
        // 幂等：同一 leadId 只归因一次
        if (!attributedLeadIds.add(event.leadId())) {
            log.debug("LeadConvertedEvent 重复投递，跳过归因 leadId={}", event.leadId());
            return;
        }
        // AnalyticsEventInput(eventType, pageId, variantId, payload, clientTs, utm)
        // 线索转化归因：eventType=lead_converted，pageId 留空（聚合级事件非页内事件）。
        // G1 修复：clientTs 用 event.convertedAt()（真实转化时刻），不用 handler 调度时的 Instant.now()
        //（@Async + AFTER_COMMIT 下 wall-clock 可能差秒级，污染转化时间分析）。
        // payload 携带 leadId（供归因回溯到具体线索）。
        AnalyticsEventInput ev = new AnalyticsEventInput(
                "lead_converted", null, null,
                "{\"leadId\":\"" + event.leadId() + "\"}",
                event.convertedAt().toEpochMilli(), null);
        analyticsIngestPort.receiveBatch(event.siteId(), List.of(ev), null, null);
    }
}

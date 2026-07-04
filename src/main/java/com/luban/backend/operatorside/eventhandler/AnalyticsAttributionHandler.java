package com.luban.backend.operatorside.eventhandler;

import com.luban.backend.shared.domain.event.LeadConvertedEvent;
import com.luban.backend.shared.dto.AnalyticsEventInput;
import com.luban.backend.shared.port.AnalyticsIngestPort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.List;

/**
 * 线索转化归因处理器（backend-ddd-refactor plan v2 T4）。
 *
 * <p>消费 {@link LeadConvertedEvent}，向 Analytics 发送转化事件用于归因统计。
 * AFTER_COMMIT + @Async：不阻塞主事务。
 */
@Component
public class AnalyticsAttributionHandler {

    private final AnalyticsIngestPort analyticsIngestPort;

    public AnalyticsAttributionHandler(AnalyticsIngestPort analyticsIngestPort) {
        this.analyticsIngestPort = analyticsIngestPort;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(LeadConvertedEvent event) {
        // AnalyticsEventInput(eventType, pageId, variantId, payload, clientTs, utm)
        // 线索转化归因：eventType=lead_converted，pageId 留空（聚合级事件非页内事件），
        // clientTs 用 Long（record 声明为 boxed Long，避免拆箱 NPE）。
        AnalyticsEventInput ev = new AnalyticsEventInput(
                "lead_converted", null, null, null,
                Instant.now().toEpochMilli(), null);
        analyticsIngestPort.receiveBatch(event.siteId(), List.of(ev), null, null);
    }
}

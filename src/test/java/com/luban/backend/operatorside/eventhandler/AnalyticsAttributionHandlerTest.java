package com.luban.backend.operatorside.eventhandler;

import com.luban.backend.shared.domain.event.LeadConvertedEvent;
import com.luban.backend.shared.dto.AnalyticsEventInput;
import com.luban.backend.shared.port.AnalyticsIngestPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * AnalyticsAttributionHandler 单测（backend-ddd-refactor plan v2 T4）。
 *
 * <p>验证 LeadConvertedEvent → Analytics 归因事件投递链路：
 * 构造的 AnalyticsEventInput eventType=lead_converted，正确投递到 ingest port。
 * 锁定 AnalyticsEventInput 的 6 参数构造（防回归到 7 参数错误）。
 */
@ExtendWith(MockitoExtension.class)
class AnalyticsAttributionHandlerTest {

    @Mock private AnalyticsIngestPort analyticsIngestPort;

    private AnalyticsAttributionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new AnalyticsAttributionHandler(analyticsIngestPort);
    }

    @Test
    void emitsLeadConvertedAnalyticsEvent() {
        Instant convertedAt = Instant.now();
        LeadConvertedEvent event = new LeadConvertedEvent(
                "lead-1", "site-1", convertedAt, Instant.now());

        handler.on(event);

        ArgumentCaptor<java.util.List<AnalyticsEventInput>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(analyticsIngestPort).receiveBatch(
                org.mockito.ArgumentMatchers.eq("site-1"), captor.capture(),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull());

        assertThat(captor.getValue()).hasSize(1);
        AnalyticsEventInput emitted = captor.getValue().get(0);
        assertThat(emitted.eventType()).isEqualTo("lead_converted");
        assertThat(emitted.clientTs()).isNotNull().isPositive();
    }
}

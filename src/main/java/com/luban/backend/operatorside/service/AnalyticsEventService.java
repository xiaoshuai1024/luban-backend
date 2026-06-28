package com.luban.backend.operatorside.service;
import com.luban.backend.shared.crypto.LeadCryptoService;

import com.luban.backend.shared.dto.AnalyticsEventInput;
import com.luban.backend.shared.port.AnalyticsIngestPort;import com.luban.backend.shared.entity.AnalyticsEvent;
import com.luban.backend.shared.mapper.AnalyticsEventMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 事件接收服务（v02 analytics 域，T-be-6）。
 *
 * 批量接收埋点事件，AES 脱敏 source_ip（复用 LeadCryptoService），落库 analytics_events。
 * 限流：单批最多 50 条（防滥用）。
 */
@Service
public class AnalyticsEventService implements AnalyticsIngestPort {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsEventService.class);
    private static final int MAX_BATCH = 50;

    private final AnalyticsEventMapper eventMapper;
    private final LeadCryptoService cryptoService;

    public AnalyticsEventService(AnalyticsEventMapper eventMapper, LeadCryptoService cryptoService) {
        this.eventMapper = eventMapper;
        this.cryptoService = cryptoService;
    }

    /**
     * 批量接收事件。
     * @param siteId     站点 ID
     * @param events     事件列表
     * @param sourceIp   访客 IP（AES 哈希后存储，不存原值）
     * @param visitorId  访客 ID（从 cookie 解析）
     * @return 实际接收数
     */
    public int receiveBatch(String siteId, List<AnalyticsEventInput> events, String sourceIp, String visitorId) {
        if (events == null || events.isEmpty()) return 0;
        // 限流：截断超量批次
        List<AnalyticsEventInput> batch = events.size() > MAX_BATCH ? events.subList(0, MAX_BATCH) : events;
        String ipHashed = cryptoService.encrypt(sourceIp);  // AES 脱敏，null 安全

        int count = 0;
        Instant now = Instant.now();
        for (AnalyticsEventInput input : batch) {
            try {
                AnalyticsEvent event = new AnalyticsEvent();
                event.setId(UUID.randomUUID().toString());
                event.setSiteId(siteId);
                event.setVisitorId(visitorId);
                event.setEventType(input.eventType());
                event.setEventPayload(input.payload());
                event.setPageId(input.pageId());
                event.setVariantId(input.variantId());
                event.setUtmJson(input.utm());
                event.setClientTs(input.clientTs() != null ? Instant.ofEpochMilli(input.clientTs()) : null);
                event.setServerTs(now);
                event.setSourceIpHashed(ipHashed);
                eventMapper.insert(event);
                count++;
            } catch (Exception e) {
                log.warn("analytics event insert failed siteId={}: {}", siteId, e.getMessage());
            }
        }
        return count;
    }
}

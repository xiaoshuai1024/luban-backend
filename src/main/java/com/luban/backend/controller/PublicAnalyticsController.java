package com.luban.backend.controller;

import com.luban.backend.dto.AnalyticsEventInput;
import com.luban.backend.service.AnalyticsEventService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 公开埋点接收控制器（v02 analytics 域，T-be-6）。
 *
 * POST /public/analytics/events（免鉴权，AuthFilter 放行 /public/*）。
 * 接收批量埋点事件，AES 脱敏 source_ip 后落库。
 *
 * visitorId 从请求头 X-Visitor-Id 或 query 传入（website server middleware 注入）。
 */
@RestController
@RequestMapping("/public/analytics")
public class PublicAnalyticsController {

    private final AnalyticsEventService eventService;

    public PublicAnalyticsController(AnalyticsEventService eventService) {
        this.eventService = eventService;
    }

    /**
     * 批量接收埋点事件。
     * Body: { siteId, events: [{eventType, pageId?, variantId?, payload?, clientTs?, utm?}] }
     */
    @PostMapping("/events")
    public Map<String, Object> receiveEvents(
            @RequestBody AnalyticsBatchRequest req,
            HttpServletRequest httpRequest) {
        String sourceIp = resolveIp(httpRequest);
        String visitorId = httpRequest.getHeader("X-Visitor-Id");
        int accepted = eventService.receiveBatch(req.siteId(), req.events(), sourceIp, visitorId);
        return Map.of("accepted", accepted);
    }

    /** IP 解析（对齐 PublicLeadController：信任 X-Forwarded-For 仅在配置时）。 */
    private String resolveIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return req.getRemoteAddr();
    }

    /** 批量请求体。 */
    public record AnalyticsBatchRequest(String siteId, List<AnalyticsEventInput> events) {}
}

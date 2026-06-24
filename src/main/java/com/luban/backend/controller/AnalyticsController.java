package com.luban.backend.controller;

import com.luban.backend.service.AnalyticsQueryService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

/**
 * Analytics 查询控制器（v02 analytics 域，T-be-8）。
 *
 * 路由（方案 §9.2，鉴权由 BFF 注入 X-User-ID，多租户按 siteId 隔离）：
 * <ul>
 *   <li>GET /analytics/overview?siteId&from&to</li>
 *   <li>GET /analytics/funnel?siteId&from&to&pageId?</li>
 *   <li>GET /analytics/attribution?siteId&from&to</li>
 *   <li>GET /analytics/trend?siteId&from&to&metric</li>
 * </ul>
 */
@RestController
@RequestMapping("/analytics")
public class AnalyticsController {

    private final AnalyticsQueryService queryService;

    public AnalyticsController(AnalyticsQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/overview")
    public Map<String, Object> overview(@RequestParam String siteId,
                                        @RequestParam String from,
                                        @RequestParam String to) {
        return queryService.getOverview(siteId, LocalDate.parse(from), LocalDate.parse(to));
    }

    @GetMapping("/funnel")
    public Map<String, Object> funnel(@RequestParam String siteId,
                                      @RequestParam String from,
                                      @RequestParam String to,
                                      @RequestParam(required = false) String pageId) {
        return queryService.getFunnel(siteId, LocalDate.parse(from), LocalDate.parse(to), pageId);
    }

    @GetMapping("/attribution")
    public Map<String, Object> attribution(@RequestParam String siteId,
                                           @RequestParam String from,
                                           @RequestParam String to) {
        return queryService.getAttribution(siteId, LocalDate.parse(from), LocalDate.parse(to));
    }

    @GetMapping("/trend")
    public Map<String, Object> trend(@RequestParam String siteId,
                                     @RequestParam String from,
                                     @RequestParam String to,
                                     @RequestParam(defaultValue = "views") String metric) {
        return queryService.getTrend(siteId, LocalDate.parse(from), LocalDate.parse(to), metric);
    }
}

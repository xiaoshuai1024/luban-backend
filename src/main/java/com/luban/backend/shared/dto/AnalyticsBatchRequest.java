package com.luban.backend.shared.dto;

import java.util.List;

/**
 * 批量埋点请求体（v02 analytics 域，T-be-6）。
 *
 * <p>POST /public/analytics/events 的请求 DTO。从 PublicAnalyticsController 内嵌 record 上提至
 * shared/dto（阶段 5 DTO 上提），使 controller 不持有 DTO 定义，便于 ArchUnit 守护
 * controllers_should_not_reference_entities。
 *
 * @param siteId 站点 id
 * @param events 批量事件列表
 */
public record AnalyticsBatchRequest(String siteId, List<AnalyticsEventInput> events) {}

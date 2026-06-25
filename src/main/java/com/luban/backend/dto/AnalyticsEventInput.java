package com.luban.backend.dto;

/**
 * 埋点事件输入（v02 analytics 域，访客提交）。
 * eventType: page_view/form_expose/form_submit。
 */
public record AnalyticsEventInput(
    String eventType,
    String pageId,
    String variantId,
    String payload,        // JSON 字符串
    Long clientTs,         // epoch millis
    String utm             // JSON 字符串（utm 参数）
) {
}

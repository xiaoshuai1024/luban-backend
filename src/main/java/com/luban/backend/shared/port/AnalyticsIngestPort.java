package com.luban.backend.shared.port;

import com.luban.backend.shared.dto.AnalyticsEventInput;

import java.util.List;

/**
 * 埋点接收端口（app-deeplink-backend-arch plan T2）。
 *
 * <p>定义 C 端（publicside）需要的访客埋点批量接收能力契约。
 * 由 operatorside 的 {@code AnalyticsEventService} 实现，publicside 的
 * {@code PublicAnalyticsController} 仅依赖此接口，不直接依赖 operatorside 类。
 *
 * <p>依赖倒置：消除 publicside → operatorside 的直接依赖（plan §6.1 隔离）。
 */
public interface AnalyticsIngestPort {
    /**
     * 批量接收访客埋点事件（限流/入库）。
     *
     * @param siteId    站点 ID
     * @param events    事件列表
     * @param sourceIp  来源 IP
     * @param visitorId 访客 ID
     * @return 实际入库的事件数（限流后）
     */
    int receiveBatch(String siteId, List<AnalyticsEventInput> events, String sourceIp, String visitorId);
}

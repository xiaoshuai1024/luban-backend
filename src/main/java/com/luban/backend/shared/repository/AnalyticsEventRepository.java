package com.luban.backend.shared.repository;

import com.luban.backend.shared.entity.AnalyticsEvent;

import java.time.Instant;
import java.util.List;

/**
 * AnalyticsEvent 仓储接口（domain 抽象，不依赖 MyBatis）。
 *
 * <p>原始埋点事件表的写入与时间范围查询。写侧（{@code AnalyticsIngestPort}）通过本仓储落库；
 * 预聚合读侧见 {@link AnalyticsReadRepository}。
 */
public interface AnalyticsEventRepository {

    void insert(AnalyticsEvent event);

    List<AnalyticsEvent> listBySiteAndTimeRange(String siteId, Instant from, Instant to);

    /** 预聚合用：按站点 + 日期（DATE(server_ts)）查事件。 */
    List<AnalyticsEvent> listBySiteAndDate(String siteId, String date);
}

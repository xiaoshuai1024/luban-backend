package com.luban.backend.shared.repository;

import com.luban.backend.shared.entity.AnalyticsDaily;

import java.time.LocalDate;
import java.util.List;

/**
 * AnalyticsDaily 仓储接口（domain 抽象，不依赖 MyBatis）。
 *
 * <p>预聚合日表的 upsert（写）与站点日期范围/variant 聚合查询（读）。
 * 读侧投影性质——与 {@code analytics_events} 原始表解耦。
 */
public interface AnalyticsReadRepository {

    /** 原子 upsert：存在则累加，不存在则插入。 */
    void upsert(AnalyticsDaily daily);

    List<AnalyticsDaily> listBySiteAndDateRange(String siteId, LocalDate from, LocalDate to);

    /** 按 variant 聚合 views/conversions（DB 端 SUM，替代内存扫描）。返回 {@code [views, conversions]}。 */
    long[] aggregateByVariant(String siteId, String variantId);
}

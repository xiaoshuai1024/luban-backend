package com.luban.backend.operatorside.repository;

import com.luban.backend.shared.entity.AnalyticsDaily;
import com.luban.backend.shared.mapper.AnalyticsDailyMapper;
import com.luban.backend.shared.repository.AnalyticsReadRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * AnalyticsRead 仓储实现：封装 {@link AnalyticsDailyMapper}。
 * 预聚合日表 upsert 与读侧查询（投影性质）。
 */
@Repository
public class AnalyticsReadRepositoryImpl implements AnalyticsReadRepository {

    private final AnalyticsDailyMapper dailyMapper;

    public AnalyticsReadRepositoryImpl(AnalyticsDailyMapper dailyMapper) {
        this.dailyMapper = dailyMapper;
    }

    @Override
    public void upsert(AnalyticsDaily daily) {
        dailyMapper.upsert(daily);
    }

    @Override
    public List<AnalyticsDaily> listBySiteAndDateRange(String siteId, LocalDate from, LocalDate to) {
        return dailyMapper.listBySiteAndDateRange(siteId, from, to);
    }

    @Override
    public long[] aggregateByVariant(String siteId, String variantId) {
        return dailyMapper.aggregateByVariant(siteId, variantId);
    }
}

package com.luban.backend.operatorside.repository;

import com.luban.backend.shared.entity.AnalyticsEvent;
import com.luban.backend.shared.mapper.AnalyticsEventMapper;
import com.luban.backend.shared.repository.AnalyticsEventRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * AnalyticsEvent 仓储实现：封装 {@link AnalyticsEventMapper}。
 * 原始埋点事件写入与时间范围查询。
 */
@Repository
public class AnalyticsEventRepositoryImpl implements AnalyticsEventRepository {

    private final AnalyticsEventMapper eventMapper;

    public AnalyticsEventRepositoryImpl(AnalyticsEventMapper eventMapper) {
        this.eventMapper = eventMapper;
    }

    @Override
    public void insert(AnalyticsEvent event) {
        eventMapper.insert(event);
    }

    @Override
    public List<AnalyticsEvent> listBySiteAndTimeRange(String siteId, Instant from, Instant to) {
        return eventMapper.listBySiteAndTimeRange(siteId, from, to);
    }

    @Override
    public List<AnalyticsEvent> listBySiteAndDate(String siteId, String date) {
        return eventMapper.listBySiteAndDate(siteId, date);
    }
}

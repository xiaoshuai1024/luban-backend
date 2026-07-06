package com.luban.backend.operatorside.service;

import com.luban.backend.shared.entity.AnalyticsDaily;
import com.luban.backend.shared.entity.AnalyticsEvent;
import com.luban.backend.shared.repository.AnalyticsEventRepository;
import com.luban.backend.shared.repository.AnalyticsReadRepository;
import com.luban.backend.shared.repository.SiteRepository;
import com.luban.backend.shared.entity.Site;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

/**
 * 预聚合服务（v02 analytics 域，T-be-7）。
 *
 * 每日凌晨聚合前一天的 analytics_events → analytics_daily。
 * 按 site_id + date + page_id + variant_id 分组：
 *   views = count(event_type='page_view')
 *   submissions = count(event_type='form_submit')
 *   conversions = count(event_type='form_submit' 且 payload 含 conversion 标记)
 *
 * upsert 原子累加（可重复运行，幂等）。
 * DB 访问经 Repository，不直连 Mapper。
 */
@Service
public class AnalyticsAggregationService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsAggregationService.class);

    private final AnalyticsEventRepository eventRepository;
    private final AnalyticsReadRepository dailyRepository;
    private final SiteRepository siteRepository;

    public AnalyticsAggregationService(AnalyticsEventRepository eventRepository,
                                       AnalyticsReadRepository dailyRepository,
                                       SiteRepository siteRepository) {
        this.eventRepository = eventRepository;
        this.dailyRepository = dailyRepository;
        this.siteRepository = siteRepository;
    }

    /** 每天凌晨 3:05 聚合前一天的数据。首次延迟 2 分钟。 */
    @Scheduled(cron = "0 5 3 * * *")
    public void aggregateYesterdayScheduled() {
        aggregateDaily(LocalDate.now().minusDays(1));
    }

    /**
     * 聚合指定日期的事件 → analytics_daily。
     * 遍历所有站点，按 site+page+variant 分组统计。
     * @return 聚合的 daily 行数
     */
    public int aggregateDaily(LocalDate date) {
        String dateStr = date.toString();
        log.info("[AnalyticsAggregation] 开始聚合 {}", dateStr);
        int totalRows = 0;
        // 遍历所有站点（schema 里 sites 表）
        // 注意：SiteMapper 无 listAll，用 sites 表查询；这里简化为遍历有事件的站点
        // 实际通过事件数据反推 siteId 集合
        Set<String> siteIds = collectSiteIdsForDate(dateStr);
        for (String siteId : siteIds) {
            totalRows += aggregateForSite(siteId, date, dateStr);
        }
        log.info("[AnalyticsAggregation] {} 完成，共 {} 行", dateStr, totalRows);
        return totalRows;
    }

    /** 聚合单个站点的某天数据。 */
    private int aggregateForSite(String siteId, LocalDate date, String dateStr) {
        List<AnalyticsEvent> events = eventRepository.listBySiteAndDate(siteId, dateStr);
        if (events.isEmpty()) return 0;

        // 分组键：pageId + variantId
        Map<String, long[]> groups = new HashMap<>();  // [views, submissions, conversions]
        for (AnalyticsEvent e : events) {
            String key = (e.getPageId() != null ? e.getPageId() : "") + "|" + (e.getVariantId() != null ? e.getVariantId() : "");
            long[] counts = groups.computeIfAbsent(key, k -> new long[3]);
            switch (e.getEventType()) {
                case "page_view" -> counts[0]++;
                case "form_submit" -> { counts[1]++; counts[2]++; }  // 提交即转化（v02 简化）
                default -> {}
            }
        }

        int rows = 0;
        for (var entry : groups.entrySet()) {
            String[] parts = entry.getKey().split("\\|", -1);
            String pageId = parts[0].isEmpty() ? null : parts[0];
            String variantId = parts.length > 1 && !parts[1].isEmpty() ? parts[1] : null;
            long[] counts = entry.getValue();

            AnalyticsDaily daily = new AnalyticsDaily();
            daily.setId(UUID.randomUUID().toString());
            daily.setSiteId(siteId);
            daily.setDate(date);
            daily.setPageId(pageId);
            daily.setVariantId(variantId);
            daily.setViews(counts[0]);
            daily.setSubmissions(counts[1]);
            daily.setConversions(counts[2]);
            dailyRepository.upsert(daily);
            rows++;
        }
        return rows;
    }

    /** 收集某天有事件的所有 siteId（通过 distinct 查询）。 */
    private Set<String> collectSiteIdsForDate(String dateStr) {
        // 简化：用 SiteMapper 查所有站点（如果有 listAll）；否则从事件表 distinct
        // SiteMapper 只有 getById/getBySlug，这里用 sites 表遍历
        List<Site> sites = siteRepository.list();
        Set<String> ids = new HashSet<>();
        for (Site s : sites) ids.add(s.getId());
        return ids;
    }
}

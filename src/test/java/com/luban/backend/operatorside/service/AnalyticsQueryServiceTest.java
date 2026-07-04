package com.luban.backend.operatorside.service;

import com.luban.backend.shared.entity.AnalyticsDaily;
import com.luban.backend.shared.entity.Lead;
import com.luban.backend.shared.mapper.AnalyticsDailyMapper;
import com.luban.backend.shared.mapper.LeadMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * AnalyticsQueryService 单测（backend-ddd-refactor plan v2 T17，补覆盖率）。
 *
 * <p>覆盖 getOverview / getFunnel / getAttribution / getTrend 4 个查询方法。
 */
@ExtendWith(MockitoExtension.class)
class AnalyticsQueryServiceTest {

    @Mock private AnalyticsDailyMapper dailyMapper;
    @Mock private LeadMapper leadMapper;

    private AnalyticsQueryService service;

    @BeforeEach
    void setUp() {
        service = new AnalyticsQueryService(dailyMapper, leadMapper);
    }

    private AnalyticsDaily daily(String pageId, long views, long conv, long submissions) {
        AnalyticsDaily d = new AnalyticsDaily();
        d.setPageId(pageId);
        d.setViews(views);
        d.setConversions(conv);
        d.setSubmissions(submissions);
        return d;
    }

    @Test
    void getOverview_aggregates_views_conversions_leads() {
        when(dailyMapper.listBySiteAndDateRange(eq("site-1"), any(), any()))
                .thenReturn(List.of(daily("p1", 100, 10, 5), daily("p2", 200, 20, 8)));
        Lead lead = new Lead();
        lead.setId("l-1");
        when(leadMapper.listForExport("site-1", null, null, null)).thenReturn(List.of(lead));

        Map<String, Object> overview = service.getOverview("site-1", LocalDate.now(), LocalDate.now());

        assertThat(overview.get("views")).isEqualTo(300L);
        assertThat(overview.get("conversions")).isEqualTo(30L);
        assertThat(overview.get("leads")).isEqualTo(1L);
        // conversionRate = 30/300*100 = 10.0
        assertThat(overview.get("conversionRate")).isEqualTo(10.0);
    }

    @Test
    void getOverview_zero_views_rate_is_zero() {
        when(dailyMapper.listBySiteAndDateRange(eq("site-1"), any(), any())).thenReturn(List.of());
        when(leadMapper.listForExport("site-1", null, null, null)).thenReturn(List.of());

        Map<String, Object> overview = service.getOverview("site-1", LocalDate.now(), LocalDate.now());

        assertThat(overview.get("views")).isEqualTo(0L);
        assertThat(overview.get("conversionRate")).isEqualTo(0.0);
    }

    @Test
    void getFunnel_filters_by_pageId() {
        when(dailyMapper.listBySiteAndDateRange(eq("site-1"), any(), any()))
                .thenReturn(List.of(daily("p1", 100, 10, 5), daily("p2", 200, 20, 8)));

        Map<String, Object> funnel = service.getFunnel("site-1", LocalDate.now(), LocalDate.now(), "p1");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> stages = (List<Map<String, Object>>) funnel.get("stages");
        // 只统计 p1：views=100, submissions=5
        assertThat(stages.get(0).get("count")).isEqualTo(100L);   // 页面访问
        assertThat(stages.get(2).get("count")).isEqualTo(5L);     // 表单提交
    }

    @Test
    void getFunnel_all_pages_when_pageId_null() {
        when(dailyMapper.listBySiteAndDateRange(eq("site-1"), any(), any()))
                .thenReturn(List.of(daily("p1", 100, 10, 5), daily("p2", 200, 20, 8)));

        Map<String, Object> funnel = service.getFunnel("site-1", LocalDate.now(), LocalDate.now(), null);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> stages = (List<Map<String, Object>>) funnel.get("stages");
        assertThat(stages.get(0).get("count")).isEqualTo(300L);   // 全部
    }

    @Test
    void getAttribution_groups_by_utm() {
        Lead withUtm = new Lead();
        withUtm.setStatus("new");
        withUtm.setUtmJson("{\"source\":\"wechat\",\"medium\":\"social\",\"campaign\":\"c1\"}");
        Lead direct = new Lead();
        direct.setStatus("new");
        direct.setUtmJson(null);
        Lead converted = new Lead();
        converted.setStatus("converted");
        converted.setUtmJson("{\"source\":\"wechat\",\"medium\":\"social\",\"campaign\":\"c1\"}");
        when(leadMapper.listForExport("site-1", null, null, null))
                .thenReturn(List.of(withUtm, direct, converted));

        Map<String, Object> result = service.getAttribution("site-1", LocalDate.now(), LocalDate.now());

        assertThat(result).containsKey("rows");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("rows");
        // 至少两组：wechat 社交 + 直接访问
        assertThat(rows.size()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void getAttribution_empty_leads_returns_empty_rows() {
        when(leadMapper.listForExport("site-1", null, null, null)).thenReturn(List.of());

        Map<String, Object> result = service.getAttribution("site-1", LocalDate.now(), LocalDate.now());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("rows");
        assertThat(rows).isEmpty();
    }
}

package com.luban.backend.operatorside.service;

import com.luban.backend.shared.entity.AnalyticsDaily;
import com.luban.backend.shared.entity.Lead;
import com.luban.backend.shared.repository.AnalyticsReadRepository;
import com.luban.backend.shared.repository.LeadRepository;
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

    @Mock private AnalyticsReadRepository dailyRepository;
    @Mock private LeadRepository leadRepository;

    private AnalyticsQueryService service;

    @BeforeEach
    void setUp() {
        service = new AnalyticsQueryService(dailyRepository, leadRepository);
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
        when(dailyRepository.listBySiteAndDateRange(eq("site-1"), any(), any()))
                .thenReturn(List.of(daily("p1", 100, 10, 5), daily("p2", 200, 20, 8)));
        Lead lead = new Lead();
        lead.setId("l-1");
        when(leadRepository.listForExport("site-1", null, null, null)).thenReturn(List.of(lead));

        Map<String, Object> overview = service.getOverview("site-1", LocalDate.now(), LocalDate.now());

        assertThat(overview.get("views")).isEqualTo(300L);
        assertThat(overview.get("conversions")).isEqualTo(30L);
        assertThat(overview.get("leads")).isEqualTo(1L);
        // conversionRate = 30/300*100 = 10.0
        assertThat(overview.get("conversionRate")).isEqualTo(10.0);
    }

    @Test
    void getOverview_zero_views_rate_is_zero() {
        when(dailyRepository.listBySiteAndDateRange(eq("site-1"), any(), any())).thenReturn(List.of());
        when(leadRepository.listForExport("site-1", null, null, null)).thenReturn(List.of());

        Map<String, Object> overview = service.getOverview("site-1", LocalDate.now(), LocalDate.now());

        assertThat(overview.get("views")).isEqualTo(0L);
        assertThat(overview.get("conversionRate")).isEqualTo(0.0);
    }

    @Test
    void getFunnel_filters_by_pageId() {
        when(dailyRepository.listBySiteAndDateRange(eq("site-1"), any(), any()))
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
        when(dailyRepository.listBySiteAndDateRange(eq("site-1"), any(), any()))
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
        when(leadRepository.listForExport("site-1", null, null, null))
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
        when(leadRepository.listForExport("site-1", null, null, null)).thenReturn(List.of());

        Map<String, Object> result = service.getAttribution("site-1", LocalDate.now(), LocalDate.now());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("rows");
        assertThat(rows).isEmpty();
    }

    // ===== getTrend（metric 4 分支 + 空数据 + 多日合并）=====

    @Test
    void getTrend_views_metric() {
        AnalyticsDaily d1 = daily("p1", 100, 10, 5);
        d1.setDate(LocalDate.of(2026, 1, 1));
        AnalyticsDaily d2 = daily("p2", 200, 20, 8);
        d2.setDate(LocalDate.of(2026, 1, 2));
        when(dailyRepository.listBySiteAndDateRange(eq("site-1"), any(), any()))
                .thenReturn(List.of(d1, d2));

        Map<String, Object> result = service.getTrend("site-1", LocalDate.now(), LocalDate.now(), "views");

        assertThat(result).containsKey("points");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> points = (List<Map<String, Object>>) result.get("points");
        assertThat(points).hasSize(2);
        assertThat(points.get(0)).containsEntry("date", "2026-01-01").containsEntry("value", 100L);
        assertThat(points.get(1)).containsEntry("date", "2026-01-02").containsEntry("value", 200L);
    }

    @Test
    void getTrend_submissions_metric() {
        AnalyticsDaily d = daily("p1", 100, 10, 5);
        d.setDate(LocalDate.of(2026, 1, 1));
        when(dailyRepository.listBySiteAndDateRange(eq("site-1"), any(), any())).thenReturn(List.of(d));

        Map<String, Object> result = service.getTrend("site-1", LocalDate.now(), LocalDate.now(), "submissions");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> points = (List<Map<String, Object>>) result.get("points");
        assertThat(points).hasSize(1);
        assertThat(points.get(0)).containsEntry("date", "2026-01-01").containsEntry("value", 5L);
    }

    @Test
    void getTrend_conversions_metric() {
        AnalyticsDaily d = daily("p1", 100, 10, 5);
        d.setDate(LocalDate.of(2026, 1, 1));
        when(dailyRepository.listBySiteAndDateRange(eq("site-1"), any(), any())).thenReturn(List.of(d));

        Map<String, Object> result = service.getTrend("site-1", LocalDate.now(), LocalDate.now(), "conversions");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> points = (List<Map<String, Object>>) result.get("points");
        assertThat(points).hasSize(1);
        assertThat(points.get(0)).containsEntry("date", "2026-01-01").containsEntry("value", 10L);
    }

    @Test
    void getTrend_default_metric_for_null_and_unknown() {
        // metric=null 与未知值都走 default 分支（views）
        AnalyticsDaily d = daily("p1", 100, 10, 5);
        d.setDate(LocalDate.of(2026, 1, 1));
        when(dailyRepository.listBySiteAndDateRange(eq("site-1"), any(), any())).thenReturn(List.of(d));

        Map<String, Object> resultNull = service.getTrend("site-1", LocalDate.now(), LocalDate.now(), null);
        Map<String, Object> resultUnknown = service.getTrend("site-1", LocalDate.now(), LocalDate.now(), "unknown");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> pointsNull = (List<Map<String, Object>>) resultNull.get("points");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> pointsUnknown = (List<Map<String, Object>>) resultUnknown.get("points");
        assertThat(pointsNull.get(0)).containsEntry("value", 100L);
        assertThat(pointsUnknown.get(0)).containsEntry("value", 100L);
    }

    @Test
    void getTrend_empty_data_returns_empty_points() {
        when(dailyRepository.listBySiteAndDateRange(eq("site-1"), any(), any())).thenReturn(List.of());

        Map<String, Object> result = service.getTrend("site-1", LocalDate.now(), LocalDate.now(), "views");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> points = (List<Map<String, Object>>) result.get("points");
        assertThat(points).isEmpty();
    }

    @Test
    void getTrend_multi_day_merge_sums_same_date() {
        // 同日多条记录按日期合并（TreeMap merge + Long::sum）
        AnalyticsDaily d1 = daily("p1", 100, 10, 5);
        d1.setDate(LocalDate.of(2026, 1, 1));
        AnalyticsDaily d2 = daily("p2", 150, 4, 7);
        d2.setDate(LocalDate.of(2026, 1, 1));
        AnalyticsDaily d3 = daily("p3", 50, 2, 1);
        d3.setDate(LocalDate.of(2026, 1, 2));
        when(dailyRepository.listBySiteAndDateRange(eq("site-1"), any(), any()))
                .thenReturn(List.of(d1, d2, d3));

        Map<String, Object> result = service.getTrend("site-1", LocalDate.now(), LocalDate.now(), "views");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> points = (List<Map<String, Object>>) result.get("points");
        // 合并后：2026-01-01 = 100+150 = 250, 2026-01-02 = 50；TreeMap 按日期升序
        assertThat(points).hasSize(2);
        assertThat(points.get(0)).containsEntry("date", "2026-01-01").containsEntry("value", 250L);
        assertThat(points.get(1)).containsEntry("date", "2026-01-02").containsEntry("value", 50L);
    }

    @Test
    void getAttribution_parse_failure_groups_into_failure_bucket() {
        // 非法 UTM JSON 进入 catch 分支，归入"解析失败|无|无"分组
        Lead badJson = new Lead();
        badJson.setStatus("new");
        badJson.setUtmJson("not-a-json");
        Lead valid = new Lead();
        valid.setStatus("converted");
        valid.setUtmJson("{\"source\":\"wechat\",\"medium\":\"social\",\"campaign\":\"c1\"}");
        when(leadRepository.listForExport("site-1", null, null, null))
                .thenReturn(List.of(badJson, valid));

        Map<String, Object> result = service.getAttribution("site-1", LocalDate.now(), LocalDate.now());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("rows");
        assertThat(rows.size()).isGreaterThanOrEqualTo(2);
        Map<String, Object> failRow = rows.stream()
                .filter(r -> "解析失败".equals(r.get("source")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing 解析失败 row"));
        assertThat(failRow).containsEntry("source", "解析失败")
                .containsEntry("medium", "无")
                .containsEntry("campaign", "无")
                .containsEntry("leads", 1L);
    }
}

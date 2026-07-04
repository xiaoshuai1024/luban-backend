package com.luban.backend.operatorside.service;

import com.luban.backend.shared.entity.AnalyticsDaily;
import com.luban.backend.shared.entity.AnalyticsEvent;
import com.luban.backend.shared.entity.Site;
import com.luban.backend.shared.mapper.AnalyticsDailyMapper;
import com.luban.backend.shared.mapper.AnalyticsEventMapper;
import com.luban.backend.shared.mapper.SiteMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AnalyticsAggregationService 单测（backend-ddd-refactor T17）。
 *
 * <p>覆盖 ETL 聚合管道：空站点集、无事件站点跳过、page_view/form_submit 分组统计、
 * unknown 事件类型忽略、null pageId/variantId 处理、upsert 调用。
 * （@Scheduled 由集成测试验证；本类直接调用 aggregateDaily。）
 */
@ExtendWith(MockitoExtension.class)
class AnalyticsAggregationServiceTest {

    @Mock private AnalyticsEventMapper eventMapper;
    @Mock private AnalyticsDailyMapper dailyMapper;
    @Mock private SiteMapper siteMapper;

    private AnalyticsAggregationService service;

    @BeforeEach
    void setUp() {
        service = new AnalyticsAggregationService(eventMapper, dailyMapper, siteMapper);
    }

    private Site site(String id) {
        Site s = new Site();
        s.setId(id);
        return s;
    }

    private AnalyticsEvent event(String type, String pageId, String variantId) {
        AnalyticsEvent e = new AnalyticsEvent();
        e.setEventType(type);
        e.setPageId(pageId);
        e.setVariantId(variantId);
        return e;
    }

    @Test
    void aggregateDaily_noSites_returnsZeroWithoutUpsert() {
        when(siteMapper.list()).thenReturn(List.of());

        int rows = service.aggregateDaily(LocalDate.of(2026, 7, 1));

        assertThat(rows).isZero();
        verify(dailyMapper, never()).upsert(any());
    }

    @Test
    void aggregateDaily_siteWithNoEvents_returnsZero() {
        when(siteMapper.list()).thenReturn(List.of(site("s-1")));
        when(eventMapper.listBySiteAndDate("s-1", "2026-07-01")).thenReturn(List.of());

        int rows = service.aggregateDaily(LocalDate.of(2026, 7, 1));

        assertThat(rows).isZero();
        verify(dailyMapper, never()).upsert(any());
    }

    @Test
    void aggregateDaily_countsViewsAndSubmissionsGroupedByPageVariant() {
        when(siteMapper.list()).thenReturn(List.of(site("s-1")));
        // pageA: 2 views + 1 submit; pageB(variant v1): 1 view
        when(eventMapper.listBySiteAndDate("s-1", "2026-07-01")).thenReturn(List.of(
                event("page_view", "pageA", null),
                event("page_view", "pageA", null),
                event("form_submit", "pageA", null),
                event("page_view", "pageB", "v1"),
                event("unknown_type", "pageA", null)   // 忽略未知类型
        ));

        int rows = service.aggregateDaily(LocalDate.of(2026, 7, 1));

        assertThat(rows).isEqualTo(2);   // 2 个分组键（pageA|、pageB|v1）

        ArgumentCaptor<AnalyticsDaily> captor = ArgumentCaptor.forClass(AnalyticsDaily.class);
        verify(dailyMapper, times(2)).upsert(captor.capture());
        // 找到 pageA 行：views=2, submissions=1, conversions=1
        AnalyticsDaily pageARow = captor.getAllValues().stream()
                .filter(d -> "pageA".equals(d.getPageId())).findFirst().orElseThrow();
        assertThat(pageARow.getViews()).isEqualTo(2);
        assertThat(pageARow.getSubmissions()).isEqualTo(1);
        assertThat(pageARow.getConversions()).isEqualTo(1);   // form_submit 即转化
        assertThat(pageARow.getVariantId()).isNull();
        assertThat(pageARow.getSiteId()).isEqualTo("s-1");
        assertThat(pageARow.getDate()).isEqualTo(LocalDate.of(2026, 7, 1));

        AnalyticsDaily pageBRow = captor.getAllValues().stream()
                .filter(d -> "pageB".equals(d.getPageId())).findFirst().orElseThrow();
        assertThat(pageBRow.getViews()).isEqualTo(1);
        assertThat(pageBRow.getSubmissions()).isZero();
        assertThat(pageBRow.getVariantId()).isEqualTo("v1");
    }

    @Test
    void aggregateDaily_multipleSites_aggregatesEachIndependently() {
        when(siteMapper.list()).thenReturn(List.of(site("s-1"), site("s-2")));
        when(eventMapper.listBySiteAndDate(eq("s-1"), eq("2026-07-01")))
                .thenReturn(List.of(event("page_view", "p1", null)));
        when(eventMapper.listBySiteAndDate(eq("s-2"), eq("2026-07-01")))
                .thenReturn(List.of(event("form_submit", "p2", null)));

        int rows = service.aggregateDaily(LocalDate.of(2026, 7, 1));

        assertThat(rows).isEqualTo(2);   // 每站 1 行
        ArgumentCaptor<AnalyticsDaily> captor = ArgumentCaptor.forClass(AnalyticsDaily.class);
        verify(dailyMapper, times(2)).upsert(captor.capture());
        assertThat(captor.getAllValues()).extracting(AnalyticsDaily::getSiteId)
                .containsExactlyInAnyOrder("s-1", "s-2");
    }

    @Test
    void aggregateDaily_nullPageAndVariant_handledAsEmptyKey() {
        when(siteMapper.list()).thenReturn(List.of(site("s-1")));
        when(eventMapper.listBySiteAndDate("s-1", "2026-07-01"))
                .thenReturn(List.of(event("page_view", null, null)));

        int rows = service.aggregateDaily(LocalDate.of(2026, 7, 1));

        assertThat(rows).isEqualTo(1);
        ArgumentCaptor<AnalyticsDaily> captor = ArgumentCaptor.forClass(AnalyticsDaily.class);
        verify(dailyMapper).upsert(captor.capture());
        assertThat(captor.getValue().getPageId()).isNull();
        assertThat(captor.getValue().getVariantId()).isNull();
    }
}

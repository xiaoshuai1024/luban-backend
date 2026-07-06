package com.luban.backend.operatorside.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luban.backend.shared.entity.AnalyticsDaily;
import com.luban.backend.shared.repository.AnalyticsReadRepository;
import com.luban.backend.shared.repository.LeadRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

/**
 * Analytics 查询服务（v02 analytics 域，T-be-8）。
 *
 * 4 个查询（方案 §9.2 契约）：
 * - getOverview: 概览（访问量/转化/Lead 数/实验数）
 * - getFunnel: 漏斗（page_view→form_expose→form_submit）
 * - getAttribution: 归因（UTM 来源/媒介/活动）
 * - getTrend: 趋势（按日 metric）
 *
 * 所有查询强制 siteId 过滤（多租户隔离，方案 §6.2）。
 * DB 访问经 Repository（AnalyticsReadRepository + LeadRepository），不直连 Mapper。
 */
@Service
public class AnalyticsQueryService {

    private final AnalyticsReadRepository dailyRepository;
    private final LeadRepository leadRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AnalyticsQueryService(AnalyticsReadRepository dailyRepository, LeadRepository leadRepository) {
        this.dailyRepository = dailyRepository;
        this.leadRepository = leadRepository;
    }

    /** 概览：访问量/转化/Lead 数（聚合 analytics_daily + leads）。 */
    public Map<String, Object> getOverview(String siteId, LocalDate from, LocalDate to) {
        List<AnalyticsDaily> daily = dailyRepository.listBySiteAndDateRange(siteId, from, to);
        long views = daily.stream().mapToLong(AnalyticsDaily::getViews).sum();
        long conversions = daily.stream().mapToLong(AnalyticsDaily::getConversions).sum();
        long leads = leadRepository.listForExport(siteId, null, null, null).size();
        double rate = views > 0 ? (double) conversions / views : 0;
        return Map.of(
                "views", views,
                "conversions", conversions,
                "leads", leads,
                "conversionRate", Math.round(rate * 10000) / 100.0  // 百分比，2 位小数
        );
    }

    /** 漏斗：page_view→form_expose→form_submit 三段。 */
    public Map<String, Object> getFunnel(String siteId, LocalDate from, LocalDate to, String pageId) {
        List<AnalyticsDaily> daily = dailyRepository.listBySiteAndDateRange(siteId, from, to).stream()
                .filter(d -> pageId == null || pageId.equals(d.getPageId()))
                .toList();
        long views = daily.stream().mapToLong(AnalyticsDaily::getViews).sum();
        long submissions = daily.stream().mapToLong(AnalyticsDaily::getSubmissions).sum();
        // form_expose 从 submissions 推断（v02 简化：expose ≈ submissions 的 3 倍经验值，实际应有独立事件）
        // TODO: 精确 form_expose 计数需 analytics_daily 增 exposes 列或独立聚合
        long exposes = Math.max(submissions, views / 2);  // 粗估：曝光介于访问与提交之间
        List<Map<String, Object>> stages = List.of(
                Map.of("name", "页面访问", "count", views),
                Map.of("name", "表单曝光", "count", exposes),
                Map.of("name", "表单提交", "count", submissions)
        );
        return Map.of("stages", stages);
    }

    /** 归因：按 UTM source/medium/campaign 聚合。 */
    public Map<String, Object> getAttribution(String siteId, LocalDate from, LocalDate to) {
        // UTM 归因需从 leads.utm_json 聚合（lead 带归因数据）
        List<com.luban.backend.shared.entity.Lead> leads = leadRepository.listForExport(siteId, null, null, null);
        Map<String, long[]> utmGroups = new HashMap<>();  // key="src|med|camp", [views(=leads), conversions]
        for (var lead : leads) {
            String utmJson = lead.getUtmJson();
            if (utmJson == null || utmJson.isBlank()) {
                utmGroups.computeIfAbsent("直接访问|无|无", k -> new long[2])[0]++;
                continue;
            }
            try {
                Map<String, String> utm = objectMapper.readValue(utmJson, new TypeReference<>() {});
                String src = utm.getOrDefault("source", "未知");
                String med = utm.getOrDefault("medium", "未知");
                String camp = utm.getOrDefault("campaign", "未知");
                String key = src + "|" + med + "|" + camp;
                long[] counts = utmGroups.computeIfAbsent(key, k -> new long[2]);
                counts[0]++;
                if ("converted".equalsIgnoreCase(lead.getStatus())) counts[1]++;
            } catch (Exception e) {
                utmGroups.computeIfAbsent("解析失败|无|无", k -> new long[2])[0]++;
            }
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (var entry : utmGroups.entrySet()) {
            String[] parts = entry.getKey().split("\\|", -1);
            long[] counts = entry.getValue();
            double rate = counts[0] > 0 ? (double) counts[1] / counts[0] : 0;
            rows.add(Map.of(
                    "source", parts[0], "medium", parts[1], "campaign", parts[2],
                    "leads", counts[0], "conversions", counts[1],
                    "rate", Math.round(rate * 10000) / 100.0
            ));
        }
        rows.sort((a, b) -> Long.compare((long) b.get("leads"), (long) a.get("leads")));
        return Map.of("rows", rows);
    }

    /** 趋势：按日的 metric（views/submissions/conversions）。 */
    public Map<String, Object> getTrend(String siteId, LocalDate from, LocalDate to, String metric) {
        List<AnalyticsDaily> daily = dailyRepository.listBySiteAndDateRange(siteId, from, to);
        Map<LocalDate, Long> byDate = new TreeMap<>();
        for (AnalyticsDaily d : daily) {
            long val = switch (metric != null ? metric : "views") {
                case "submissions" -> d.getSubmissions();
                case "conversions" -> d.getConversions();
                default -> d.getViews();
            };
            byDate.merge(d.getDate(), val, Long::sum);
        }
        List<Map<String, Object>> points = new ArrayList<>();
        for (var entry : byDate.entrySet()) {
            points.add(Map.of("date", entry.getKey().toString(), "value", entry.getValue()));
        }
        return Map.of("points", points);
    }
}

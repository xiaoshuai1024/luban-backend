package com.luban.backend.shared.repository;

import com.luban.backend.shared.domain.AbExperimentAggregate;
import com.luban.backend.shared.entity.AbAssignment;
import com.luban.backend.shared.entity.AbExperiment;
import com.luban.backend.shared.entity.AbVariant;
import com.luban.backend.shared.entity.AnalyticsDaily;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * AB 实验仓储接口（backend-ddd-refactor plan v2 T9）。
 * 封装 AbExperimentMapper + AbVariantMapper + AbAssignmentMapper + AnalyticsDailyMapper（读模型），
 * AbService 零领域 Mapper 依赖。
 *
 * <p>G1 补：AbAssignment 写入（assignVariant 路径）+ AnalyticsDaily 读取（显著性计算）经此仓储，
 * 让 AbService 不再直接依赖 Mapper（ArchUnit services_should_not_depend_on_mapper 守护）。
 */
public interface AbExperimentRepository {
    Optional<AbExperimentAggregate> findById(String experimentId);
    List<AbExperiment> listBySiteId(String siteId, String status);
    AbExperiment findRunningByPageId(String pageId);
    List<AbVariant> listVariantsByExperimentId(String experimentId);
    void save(AbExperimentAggregate aggregate);

    /** 查已分配的分桶（幂等：同 visitor+experiment 返回既有 assignment）。 */
    AbAssignment getAssignment(String visitorId, String experimentId);

    /** 持久化分桶分配（INSERT IGNORE 幂等，防并发重复）。 */
    void saveAssignment(AbAssignment assignment);

    /** 读模型：按站点 + 日期范围查 analytics_daily（显著性计算用）。 */
    List<AnalyticsDaily> listDailyBySiteAndDateRange(String siteId, LocalDate from, LocalDate to);

    /** G1 修复 N3：按 variant SUM views/conversions（SQL 聚合，替代内存 stream）。 */
    long[] aggregateDailyByVariant(String siteId, String variantId);
}

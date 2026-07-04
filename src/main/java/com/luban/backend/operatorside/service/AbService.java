package com.luban.backend.operatorside.service;

import com.luban.backend.shared.domain.AbExperimentAggregate;
import com.luban.backend.shared.entity.*;
import com.luban.backend.shared.exception.BusinessException;
import com.luban.backend.shared.repository.AbExperimentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * AB 实验服务（v02 ab 域，T-be-9/10/11）。
 *
 * <p>v2 DDD 改造（backend-ddd-refactor plan v2 T9）：
 * 领域 Mapper（experimentMapper/variantMapper）→ {@link AbExperimentRepository}，
 * 保留 {@code assignmentMapper}（分桶记录，独立写模型）+ {@code dailyMapper}（analytics 读模型）。
 *
 * <p>核心能力：
 * <ul>
 *   <li>CRUD（创建含变体、查列表、查详情、结束）——状态转换经聚合根 {@link AbExperimentAggregate}</li>
 *   <li>一致性哈希分桶：visitor_id + experiment_id 稳定映射到 variant（幂等）</li>
 *   <li>χ² 显著性检验：委托聚合根 {@link AbExperimentAggregate#computeSignificance}（Yates 校正，
 *       修复版 erfc——旧 Service 私有实现 erfc 方向反，导致 pValue 偏大、显著差异误判为不显著）</li>
 * </ul>
 *
 * <p>约束（方案 §3.3）：
 * <ul>
 *   <li>单页单 running：同 page_id 同时只能有一个 status=running 的实验</li>
 *   <li>traffic_pct 控制实验流量（0-100，未命中实验流量走默认版本）</li>
 * </ul>
 */
@Service
public class AbService {

    private final AbExperimentRepository experimentRepository;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    public AbService(AbExperimentRepository experimentRepository,
                     org.springframework.context.ApplicationEventPublisher eventPublisher) {
        this.experimentRepository = experimentRepository;
        this.eventPublisher = eventPublisher;
    }

    /** 拉取并发布聚合根累积的领域事件（AFTER_COMMIT 由 handler 消费）。 */
    private void publishEvents(com.luban.backend.shared.domain.AbExperimentAggregate agg) {
        agg.pullEvents().forEach(eventPublisher::publishEvent);
    }

    // ===== T-be-9 CRUD =====

    /** 实验列表（按 site + status 过滤）。 */
    public List<AbExperiment> listExperiments(String siteId, String status) {
        return experimentRepository.listBySiteId(siteId, status);
    }

    /** 实验详情（含变体）。 */
    public AbExperimentDetail getExperimentDetail(String experimentId) {
        AbExperimentAggregate agg = experimentRepository.findById(experimentId)
                .orElseThrow(BusinessException::experimentNotFound);
        return new AbExperimentDetail(agg.toExperiment(), agg.variants());
    }

    /**
     * 创建实验（含变体）。
     * 约束：若 status=running，校验同 page 无其它 running 实验（单页单 running）。
     */
    @Transactional(rollbackFor = Exception.class)
    public AbExperiment createExperiment(CreateExperimentInput input) {
        // 单页单 running 校验
        if ("running".equals(input.status())) {
            AbConflictResult conflict = findRunningConflict(input.pageId(), null);
            if (conflict != null) {
                throw BusinessException.experimentConflict("该页面已有运行中的实验: " + conflict.name());
            }
        }
        Instant now = Instant.now();
        // 经聚合根工厂构建（统一入口，禁止 Service 直接 new AbExperiment()）
        AbExperimentAggregate agg = AbExperimentAggregate.newExperiment(
                UUID.randomUUID().toString(),
                input.siteId(),
                input.pageId(),
                input.name(),
                input.trafficPct() != null ? input.trafficPct() : 100,
                input.status() != null ? input.status() : "draft",
                now);
        AbExperiment exp = agg.toExperiment();

        // 批量构建变体并经聚合根追加（addVariant）
        for (var v : input.variants()) {
            AbVariant variant = new AbVariant();
            variant.setId(UUID.randomUUID().toString());
            variant.setExperimentId(exp.getId());
            variant.setLabel(v.label());
            variant.setPageVersionId(v.pageVersionId());
            variant.setWeight(v.weight() != null ? v.weight() : 50);
            variant.setControl(v.isControl() != null && v.isControl());
            agg.addVariant(variant);
        }
        experimentRepository.save(agg);
        return exp;
    }

    /** 结束实验（running→ended，聚合根状态机校验）。 */
    @Transactional(rollbackFor = Exception.class)
    public AbExperiment endExperiment(String experimentId) {
        AbExperimentAggregate agg = experimentRepository.findById(experimentId)
                .orElseThrow(BusinessException::experimentNotFound);
        agg.end();   // 聚合根状态机：running→ended（非法转换抛 invalidStateTransition）
        experimentRepository.save(agg);
        publishEvents(agg);
        return agg.toExperiment();
    }

    // ===== T-be-10 一致性哈希分桶 =====

    /**
     * 为访客分配变体（一致性哈希，幂等）。
     * 逻辑：visitor_id + experiment_id 哈希 → 0-99，< traffic_pct 则入实验；
     * 入实验后按 weight 加权分配 variant，记录到 ab_assignments（INSERT IGNORE 幂等）。
     *
     * <p><b>分桶算法保留 Service 私有 stableHash</b>（任务约束：流量分配不变，避免线上分桶结果漂移）；
     * 读查询（findRunningByPage / listVariants）走 Repository。
     *
     * @return variantId；未入实验流量返回 null（走默认版本）
     */
    public AssignResult assignVariant(String visitorId, String pageId) {
        AbExperiment exp = experimentRepository.findRunningByPageId(pageId);
        if (exp == null) return new AssignResult(null, null, false);

        // 一致性哈希：visitor+experiment → 0-99
        int hash = bucketHash(visitorId + ":" + exp.getId());
        if (hash >= exp.getTrafficPct()) {
            return new AssignResult(null, null, false);  // 未命中实验流量
        }

        // 已有分桶记录 → 直接返回（幂等）
        AbAssignment existing = experimentRepository.getAssignment(visitorId, exp.getId());
        if (existing != null) {
            return new AssignResult(exp.getId(), existing.getVariantId(), true);
        }

        // 按 weight 加权分配 variant
        List<AbVariant> variants = experimentRepository.listVariantsByExperimentId(exp.getId());
        if (variants.isEmpty()) return new AssignResult(null, null, false);
        int totalWeight = variants.stream().mapToInt(AbVariant::getWeight).sum();
        if (totalWeight <= 0) return new AssignResult(null, null, false);
        int roll = bucketHash(visitorId + ":" + exp.getId() + ":variant") % totalWeight;
        int acc = 0;
        String assignedVariantId = variants.get(0).getId();
        for (AbVariant v : variants) {
            acc += v.getWeight();
            if (roll < acc) { assignedVariantId = v.getId(); break; }
        }

        // 记录分桶（INSERT IGNORE 幂等，防并发重复）
        AbAssignment assignment = new AbAssignment();
        assignment.setId(UUID.randomUUID().toString());
        assignment.setVisitorId(visitorId);
        assignment.setExperimentId(exp.getId());
        assignment.setVariantId(assignedVariantId);
        assignment.setAssignedAt(Instant.now());
        experimentRepository.saveAssignment(assignment);
        return new AssignResult(exp.getId(), assignedVariantId, true);
    }

    // ===== T-be-11 χ² 显著性检验 =====

    /**
     * 显著性检验（χ² 卡方检验，对照 vs 实验组）。
     * 数据来源：analytics_daily 按 variant_id 聚合（views/submissions）。
     *
     * <p><b>算法委托聚合根 {@link AbExperimentAggregate#computeSignificance}</b>（v2 修复）：
     * 旧 Service 私有 {@code erfc} 方向反（返回 erf 而非 erfc），导致 pValue 偏大、
     * 显著差异被误判为不显著。聚合根 erfc 已修正（对齐 Abramowitz-Stegun 7.1.26 标准）。
     *
     * @return χ² 统计量、p 值、是否显著（p&lt;0.05）、提升百分比
     */
    public SignificanceResult computeSignificance(String experimentId) {
        AbExperimentDetail detail = getExperimentDetail(experimentId);
        List<AbVariant> variants = detail.variants();
        if (variants.size() < 2) {
            throw BusinessException.insufficientVariants();
        }

        // 找对照组（isControl=true 或 label=A）
        AbVariant controlV = variants.stream().filter(AbVariant::isControl).findFirst()
                .orElse(variants.get(0));
        AbVariant experimentV = variants.stream().filter(v -> !v.getId().equals(controlV.getId())).findFirst()
                .orElse(variants.get(1));

        // 从 analytics_daily 聚合（按 variant_id）
        long[] controlStats = aggregateVariantStats(detail.experiment().getSiteId(), controlV.getId());
        long[] expStats = aggregateVariantStats(detail.experiment().getSiteId(), experimentV.getId());

        // 委托聚合根算法（修复版 erfc + Yates 校正）
        AbExperimentAggregate.VariantStats controlVs = new AbExperimentAggregate.VariantStats(
                controlV.getId(), controlStats[0], controlStats[1],
                controlStats[0] > 0 ? (double) controlStats[1] / controlStats[0] : 0);
        AbExperimentAggregate.VariantStats expVs = new AbExperimentAggregate.VariantStats(
                experimentV.getId(), expStats[0], expStats[1],
                expStats[0] > 0 ? (double) expStats[1] / expStats[0] : 0);
        AbExperimentAggregate.SignificanceResult sr =
                AbExperimentAggregate.computeSignificance(controlVs, expVs);

        return new SignificanceResult(
                experimentId,
                new VariantStats(controlV.getId(), controlStats[0], controlStats[1], controlVs.rate()),
                new VariantStats(experimentV.getId(), expStats[0], expStats[1], expVs.rate()),
                sr.chiSquare(), sr.pValue(), sr.isSignificant(), sr.lift()
        );
    }

    /** 聚合某变体的 views/conversions（从 analytics_daily）。 */
    private long[] aggregateVariantStats(String siteId, String variantId) {
        // G1 修复 N3：SQL 聚合 SUM（替代 5 年窗口内存 stream 过滤的 O(n) 扫描）
        long[] stats = experimentRepository.aggregateDailyByVariant(siteId, variantId);
        return new long[]{stats[0], stats[1]};   // {views, conversions}
    }

    // ===== 内部工具 =====

    /** 查找同页面的 running 实验冲突。 */
    private AbConflictResult findRunningConflict(String pageId, String excludeExperimentId) {
        AbConflictResult existing = null;
        AbExperiment running = experimentRepository.findRunningByPageId(pageId);
        if (running != null && !running.getId().equals(excludeExperimentId)) {
            existing = new AbConflictResult(running.getId(), running.getName());
        }
        return existing;
    }

    /**
     * 稳定哈希（FNV-1a 32bit → 0-99 范围），同输入同输出。
     *
     * <p>分桶算法委托 {@link AbExperimentAggregate#stableHash}（聚合根统一 FNV-1a 实现，
     * 单一事实来源）；本方法仅做流量百分比折叠（Math.abs % 100）。
     */
    private static int bucketHash(String key) {
        return Math.abs(AbExperimentAggregate.stableHash(key)) % 100;
    }

    // ===== DTO records =====

    public record CreateExperimentInput(
            String siteId, String pageId, String name, String status, Integer trafficPct,
            List<VariantInput> variants) {}
    public record VariantInput(String label, String pageVersionId, Integer weight, Boolean isControl) {}
    public record AbExperimentDetail(AbExperiment experiment, List<AbVariant> variants) {}
    public record AssignResult(String experimentId, String variantId, boolean inExperiment) {}
    public record SignificanceResult(
            String experimentId, VariantStats control, VariantStats experiment,
            double chiSquare, double pValue, boolean isSignificant, double lift) {}
    public record VariantStats(String variantId, long views, long conversions, double rate) {}
    private record AbConflictResult(String id, String name) {}
}

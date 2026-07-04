package com.luban.backend.operatorside.service;

import com.luban.backend.shared.entity.*;
import com.luban.backend.shared.exception.BusinessException;
import com.luban.backend.shared.mapper.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * AB 实验服务（v02 ab 域，T-be-9/10/11）。
 *
 * 核心能力：
 * - CRUD（创建含变体、查列表、查详情、结束）
 * - 一致性哈希分桶：visitor_id + experiment_id 稳定映射到 variant（幂等）
 * - χ² 显著性检验：对照 vs 实验组的转化差异是否显著（p<0.05）
 *
 * 约束（方案 §3.3）：
 * - 单页单 running：同 page_id 同时只能有一个 status=running 的实验
 * - traffic_pct 控制实验流量（0-100，未命中实验流量走默认版本）
 */
@Service
public class AbService {

    private final AbExperimentMapper experimentMapper;
    private final AbVariantMapper variantMapper;
    private final AbAssignmentMapper assignmentMapper;
    private final AnalyticsDailyMapper dailyMapper;

    public AbService(AbExperimentMapper experimentMapper, AbVariantMapper variantMapper,
                     AbAssignmentMapper assignmentMapper, AnalyticsDailyMapper dailyMapper) {
        this.experimentMapper = experimentMapper;
        this.variantMapper = variantMapper;
        this.assignmentMapper = assignmentMapper;
        this.dailyMapper = dailyMapper;
    }

    // ===== T-be-9 CRUD =====

    /** 实验列表（按 site + status 过滤）。 */
    public List<AbExperiment> listExperiments(String siteId, String status) {
        return experimentMapper.listBySite(siteId, status);
    }

    /** 实验详情（含变体）。 */
    public AbExperimentDetail getExperimentDetail(String experimentId) {
        AbExperiment exp = experimentMapper.getById(experimentId);
        if (exp == null) throw new BusinessException(HttpStatus.NOT_FOUND, "EXPERIMENT_NOT_FOUND", "实验不存在");
        List<AbVariant> variants = variantMapper.listByExperiment(experimentId);
        return new AbExperimentDetail(exp, variants);
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
                throw new BusinessException(HttpStatus.CONFLICT, "EXPERIMENT_CONFLICT",
                        "该页面已有运行中的实验: " + conflict.name());
            }
        }
        Instant now = Instant.now();
        AbExperiment exp = new AbExperiment();
        exp.setId(UUID.randomUUID().toString());
        exp.setSiteId(input.siteId());
        exp.setPageId(input.pageId());
        exp.setName(input.name());
        exp.setStatus(input.status() != null ? input.status() : "draft");
        exp.setTrafficPct(input.trafficPct() != null ? input.trafficPct() : 100);
        exp.setCreatedAt(now);
        if ("running".equals(exp.getStatus())) exp.setStartAt(now);
        experimentMapper.insert(exp);

        // 批量插入变体
        List<AbVariant> variants = new ArrayList<>();
        for (var v : input.variants()) {
            AbVariant variant = new AbVariant();
            variant.setId(UUID.randomUUID().toString());
            variant.setExperimentId(exp.getId());
            variant.setLabel(v.label());
            variant.setPageVersionId(v.pageVersionId());
            variant.setWeight(v.weight() != null ? v.weight() : 50);
            variant.setControl(v.isControl() != null && v.isControl());
            variants.add(variant);
        }
        if (!variants.isEmpty()) variantMapper.batchInsert(variants);
        return exp;
    }

    /** 结束实验（status→ended）。 */
    @Transactional(rollbackFor = Exception.class)
    public AbExperiment endExperiment(String experimentId) {
        AbExperiment exp = experimentMapper.getById(experimentId);
        if (exp == null) throw new BusinessException(HttpStatus.NOT_FOUND, "EXPERIMENT_NOT_FOUND", "实验不存在");
        exp.setStatus("ended");
        exp.setEndAt(Instant.now());
        experimentMapper.updateStatus(exp);
        return exp;
    }

    // ===== T-be-10 一致性哈希分桶 =====

    /**
     * 为访客分配变体（一致性哈希，幂等）。
     * 逻辑：visitor_id + experiment_id 哈希 → 0-99，< traffic_pct 则入实验；
     * 入实验后按 weight 加权分配 variant，记录到 ab_assignments（INSERT IGNORE 幂等）。
     *
     * @return variantId；未入实验流量返回 null（走默认版本）
     */
    public AssignResult assignVariant(String visitorId, String pageId) {
        AbExperiment exp = experimentMapper.findRunningByPage(pageId);
        if (exp == null) return new AssignResult(null, null, false);

        // 一致性哈希：visitor+experiment → 0-99
        int hash = stableHash(visitorId + ":" + exp.getId()) % 100;
        if (hash >= exp.getTrafficPct()) {
            return new AssignResult(null, null, false);  // 未命中实验流量
        }

        // 已有分桶记录 → 直接返回（幂等）
        AbAssignment existing = assignmentMapper.get(visitorId, exp.getId());
        if (existing != null) {
            return new AssignResult(exp.getId(), existing.getVariantId(), true);
        }

        // 按 weight 加权分配 variant
        List<AbVariant> variants = variantMapper.listByExperiment(exp.getId());
        if (variants.isEmpty()) return new AssignResult(null, null, false);
        int totalWeight = variants.stream().mapToInt(AbVariant::getWeight).sum();
        if (totalWeight <= 0) return new AssignResult(null, null, false);
        int roll = stableHash(visitorId + ":" + exp.getId() + ":variant") % totalWeight;
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
        assignmentMapper.insert(assignment);
        return new AssignResult(exp.getId(), assignedVariantId, true);
    }

    // ===== T-be-11 χ² 显著性检验 =====

    /**
     * 显著性检验（χ² 卡方检验，对照 vs 实验组）。
     * 数据来源：analytics_daily 按 variant_id 聚合（views/submissions）。
     *
     * @return χ² 统计量、p 值、是否显著（p<0.05）、提升百分比
     */
    public SignificanceResult computeSignificance(String experimentId) {
        AbExperimentDetail detail = getExperimentDetail(experimentId);
        List<AbVariant> variants = detail.variants();
        if (variants.size() < 2) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INSUFFICIENT_VARIANTS", "至少需要 2 个变体");
        }

        // 找对照组（isControl=true 或 label=A）
        AbVariant controlV = variants.stream().filter(AbVariant::isControl).findFirst()
                .orElse(variants.get(0));
        AbVariant experimentV = variants.stream().filter(v -> !v.getId().equals(controlV.getId())).findFirst()
                .orElse(variants.get(1));

        // 从 analytics_daily 聚合（按 variant_id）
        long[] controlStats = aggregateVariantStats(detail.experiment().getSiteId(), controlV.getId());
        long[] expStats = aggregateVariantStats(detail.experiment().getSiteId(), experimentV.getId());
        // stats = [views, conversions]

        double controlRate = controlStats[0] > 0 ? (double) controlStats[1] / controlStats[0] : 0;
        double expRate = expStats[0] > 0 ? (double) expStats[1] / expStats[0] : 0;

        // χ² 检验（2x2 列联表）
        double chiSquare = computeChiSquare(controlStats[0], controlStats[1], expStats[0], expStats[1]);
        double pValue = chiSquareToPValue(chiSquare, 1);  // 自由度 1
        boolean isSignificant = pValue < 0.05;
        double lift = controlRate > 0 ? ((expRate - controlRate) / controlRate) * 100 : 0;

        return new SignificanceResult(
                experimentId,
                new VariantStats(controlV.getId(), controlStats[0], controlStats[1], controlRate),
                new VariantStats(experimentV.getId(), expStats[0], expStats[1], expRate),
                chiSquare, pValue, isSignificant, lift
        );
    }

    /** 聚合某变体的 views/conversions（从 analytics_daily）。 */
    private long[] aggregateVariantStats(String siteId, String variantId) {
        // 简化：查该 variant 所有日期的 daily 聚合
        // TODO: AnalyticsDailyMapper 需按 variant 聚合方法；当前用宽范围查询
        var daily = dailyMapper.listBySiteAndDateRange(siteId,
                java.time.LocalDate.of(2020, 1, 1), java.time.LocalDate.now());
        long views = daily.stream().filter(d -> variantId.equals(d.getVariantId()))
                .mapToLong(AnalyticsDaily::getViews).sum();
        long conversions = daily.stream().filter(d -> variantId.equals(d.getVariantId()))
                .mapToLong(AnalyticsDaily::getConversions).sum();
        return new long[]{views, conversions};
    }

    // ===== 内部工具 =====

    /** 查找同页面的 running 实验冲突。 */
    private AbConflictResult findRunningConflict(String pageId, String excludeExperimentId) {
        AbConflictResult existing = null;
        AbExperiment running = experimentMapper.findRunningByPage(pageId);
        if (running != null && !running.getId().equals(excludeExperimentId)) {
            existing = new AbConflictResult(running.getId(), running.getName());
        }
        return existing;
    }

    /** 稳定哈希（FNV-1a 32bit → 0-99 范围），同输入同输出。 */
    private static int stableHash(String key) {
        int hash = 0x811c9dc5;
        for (int i = 0; i < key.length(); i++) {
            hash ^= key.charAt(i);
            hash *= 0x01000193;
        }
        return Math.abs(hash) % 100;
    }

    /**
     * 2x2 列联表 χ² 统计量（Yates 校正）。
     * a/b = 对照组 转化/未转化；c/d = 实验组 转化/未转化。
     */
    private static double computeChiSquare(long aViews, long aConv, long bViews, long bConv) {
        long a = aConv;
        long b = aViews - aConv;
        long c = bConv;
        long d = bViews - bConv;
        long n = a + b + c + d;
        if (n == 0) return 0;
        // 期望频数 + Yates 校正
        double expectedAC = (double) (a + c) * (a + b) / n;
        double expectedBD = (double) (b + d) * (a + b) / n;
        double correction = 0.5;
        double chiA = Math.pow(Math.abs(a - expectedAC) - correction, 2) / Math.max(expectedAC, 1);
        double chiB = Math.pow(Math.abs(b - expectedBD) - correction, 2) / Math.max(expectedBD, 1);
        return chiA + chiB;
    }

    /**
     * χ² → p 值（自由度 1 的近似，使用正态近似）。
     * χ²(1) 的 p = 2 * (1 - Φ(√χ²))，Φ 用 erf 近似。
     */
    private static double chiSquareToPValue(double chiSquare, int df) {
        if (df != 1) return 1.0;  // 仅支持 df=1
        if (chiSquare <= 0) return 1.0;
        // p = erfc(√(χ²/2))，erfc 用 Abramowitz-Stegun 近似
        double x = Math.sqrt(chiSquare / 2);
        return erfc(x);
    }

    /**
     * 互补误差函数 erfc 近似（Abramowitz-Stegun 7.1.26）。
     *
     * <p>AS 7.1.26 标准形式给出 erf(x) = 1 - poly(t)*exp(-x²)。
     * 故 erfc(x) = 1 - erf(x) = poly(t)*exp(-x²)。
     * <p><b>修复（backend-ddd-refactor T16）</b>：旧实现返回 1-poly*exp 即 erf（方向反），
     * 导致 pValue 偏大、显著差异被误判为不显著。修正为返回真正的 erfc。
     */
    private static double erfc(double x) {
        double t = 1.0 / (1.0 + 0.3275911 * x);
        double poly = t * (0.254829592 + t * (-0.284496736 + t * (1.421413741
                + t * (-1.453152027 + t * 1.061405429))));
        return poly * Math.exp(-x * x);
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

package com.luban.backend.shared.domain;

import com.luban.backend.shared.domain.event.DomainEvent;
import com.luban.backend.shared.domain.event.ExperimentEndedEvent;
import com.luban.backend.shared.domain.event.ExperimentStartedEvent;
import com.luban.backend.shared.entity.AbExperiment;
import com.luban.backend.shared.entity.AbVariant;
import com.luban.backend.shared.exception.BusinessException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A/B 实验聚合根（backend-ddd-refactor plan v2 T9）。
 *
 * <p>封装 AB 实验域不变量（对齐 {@code .agents/rules/luban-engineering-principles.md}）：
 * <ul>
 *   <li><b>状态机</b>：draft→running→ended（显式转换，非法转换抛 invalidStateTransition）</li>
 *   <li><b>单页单 running</b>：{@link #start(boolean)} 接收"该页是否已有 running 实验"的跨聚合事实，
 *       聚合根做决策（不直接查 Mapper，DDD 边界）</li>
 *   <li><b>χ² 显著性</b>：{@link #computeSignificance}（Yates 校正 2x2 列联表，p&lt;0.05 显著）</li>
 *   <li><b>分桶</b>：{@link #stableHash}（FNV-1a 32-bit）+ {@link #selectVariantByWeight}（加权 roll）</li>
 * </ul>
 *
 * @see AbExperiment
 * @see AbVariant
 */
public final class AbExperimentAggregate {

    public static final String STATUS_DRAFT = "draft";
    public static final String STATUS_RUNNING = "running";
    public static final String STATUS_ENDED = "ended";

    private final AbExperiment root;
    private final List<AbVariant> variants;
    private final List<DomainEvent> events = new ArrayList<>();

    private AbExperimentAggregate(AbExperiment root, List<AbVariant> variants) {
        this.root = root;
        this.variants = variants != null ? new ArrayList<>(variants) : new ArrayList<>();
    }

    /** 工厂：从持久化重建（含 variants）。 */
    public static AbExperimentAggregate reconstitute(AbExperiment persisted, List<AbVariant> variants) {
        return new AbExperimentAggregate(persisted, variants);
    }

    /**
     * 工厂：创建新实验（draft 或 running）。
     *
     * <p>聚合根统一入口，避免 Service 直接 new AbExperiment() 绕过不变量。
     * 变体通过 {@link #addVariant(AbVariant)} 追加；start 前的变体数量校验在 {@link #start(boolean)} 中。
     *
     * @param id            UUID（由 Service 生成）
     * @param siteId        站点 id
     * @param pageId        页面 id
     * @param name          实验名
     * @param trafficPct    流量百分比（≤0 时默认 100）
     * @param initialStatus 初始状态（draft/running；null 视为 draft）
     * @param startAt       running 状态的启动时刻（draft 可为 null）
     */
    public static AbExperimentAggregate newExperiment(String id, String siteId, String pageId,
            String name, int trafficPct, String initialStatus, Instant startAt) {
        AbExperiment exp = new AbExperiment();
        exp.setId(id);
        exp.setSiteId(siteId);
        exp.setPageId(pageId);
        exp.setName(name);
        String status = initialStatus != null ? initialStatus : STATUS_DRAFT;
        exp.setStatus(status);
        exp.setTrafficPct(trafficPct > 0 ? trafficPct : 100);
        if (STATUS_RUNNING.equals(status)) {
            exp.setStartAt(startAt != null ? startAt : Instant.now());
        }
        exp.setCreatedAt(Instant.now());
        return new AbExperimentAggregate(exp, new ArrayList<>());
    }

    /** 追加一个变体（start 前组装 variants 集合）。 */
    public void addVariant(AbVariant variant) {
        variants.add(variant);
    }

    /**
     * 启动实验（draft→running）。
     *
     * @param pageHasRunningExperiment 该 page 是否已有 running 实验（跨聚合查询，Service 传入）
     */
    public void start(boolean pageHasRunningExperiment) {
        if (pageHasRunningExperiment) {
            throw BusinessException.experimentConflict("该页面已有运行中的实验");
        }
        assertTransition(STATUS_DRAFT, STATUS_RUNNING);
        root.setStatus(STATUS_RUNNING);
        root.setStartAt(Instant.now());
        events.add(new ExperimentStartedEvent(root.getId(), root.getSiteId(), root.getPageId(), root.getStartAt()));
    }

    /** 结束实验（running→ended）。 */
    public void end() {
        assertTransition(STATUS_RUNNING, STATUS_ENDED);
        root.setStatus(STATUS_ENDED);
        root.setEndAt(Instant.now());
        events.add(new ExperimentEndedEvent(root.getId(), root.getSiteId(), root.getPageId(), root.getEndAt()));
    }

    public AbExperiment toExperiment() {
        return root;
    }

    public List<AbVariant> variants() {
        return List.copyOf(variants);
    }

    public List<DomainEvent> pullEvents() {
        List<DomainEvent> drained = new ArrayList<>(events);
        events.clear();
        return drained;
    }

    private void assertTransition(String expectedFrom, String to) {
        if (!expectedFrom.equals(root.getStatus())) {
            throw BusinessException.invalidStateTransition(root.getStatus(), to);
        }
    }

    // ===== 纯算法（无状态 static，非聚合根不变量但属领域计算） =====

    /**
     * 稳定哈希（FNV-1a 32-bit，int 算术）。
     *
     * <p>同输入同输出，跨进程稳定，用于分桶确定性。<b>这是生产分桶的权威实现</b>——
     * AbService.assignVariant 经此方法计算 hash % 100，决定 visitor 是否进入实验流量 +
     * 命中哪个 variant。改算法会改变线上分桶结果（流量重新分配），需 QA 验证。
     *
     * <p>返回 int（可能为负，调用方取 % 后按需处理）；与旧 AbService 私有 stableHash 字节一致。
     */
    public static int stableHash(String input) {
        int hash = 0x811c9dc5;
        for (int i = 0; i < input.length(); i++) {
            hash ^= input.charAt(i);
            hash *= 0x01000193;
        }
        return hash;
    }

    /**
     * 按 weight 加权选择 variant。
     *
     * @param orderedVariants 有序 variant 列表（control 在前）
     * @param roll 0..totalWeight-1 的随机/哈希值
     * @return 命中的 variant
     */
    public static AbVariant selectVariantByWeight(List<AbVariant> orderedVariants, long roll) {
        long acc = 0;
        for (AbVariant v : orderedVariants) {
            acc += v.getWeight();
            if (roll < acc) {
                return v;
            }
        }
        // 兜底（理论上 roll < totalWeight 不会走到）：返回 control（第一个）
        return orderedVariants.isEmpty() ? null : orderedVariants.get(0);
    }

    /** variant 统计快照（聚合根 computeSignificance 的输入）。 */
    public record VariantStats(String variantId, long views, long conversions, double rate) {}

    /** 显著性结果。 */
    public record SignificanceResult(
            String controlVariantId,
            String experimentVariantId,
            double chiSquare,
            double pValue,
            boolean isSignificant,
            double lift) {}

    /**
     * 计算 χ² 显著性（2x2 列联表 + Yates 校正）。
     *
     * @param control    对照组统计
     * @param experiment 实验组统计（null 或不足 2 variant 抛 INSUFFICIENT_VARIANTS）
     * @return 显著性结果（p &lt; 0.05 为显著）
     */
    public static SignificanceResult computeSignificance(VariantStats control, VariantStats experiment) {
        if (control == null || experiment == null) {
            throw BusinessException.insufficientVariants();
        }
        long aViews = control.views();
        long aConv = control.conversions();
        long bViews = experiment.views();
        long bConv = experiment.conversions();
        double controlRate = aViews == 0 ? 0 : (double) aConv / aViews;
        double expRate = bViews == 0 ? 0 : (double) bConv / bViews;

        double chi = computeChiSquareWithYates(aViews, aConv, bViews, bConv);
        double pValue = chiSquareToPValue(chi);
        double lift = controlRate == 0 ? 0 : ((expRate - controlRate) / controlRate) * 100;

        return new SignificanceResult(
                control.variantId(), experiment.variantId(),
                chi, pValue, pValue < 0.05, lift);
    }

    /**
     * χ² 计算（2x2 + Yates 连续性校正 0.5）。
     * a=controlConv, b=controlViews-controlConv, c=expConv, d=expViews-expConv, n=a+b+c+d。
     */
    static double computeChiSquareWithYates(long aViews, long aConv, long bViews, long bConv) {
        long a = aConv;
        long b = aViews - aConv;
        long c = bConv;
        long d = bViews - bConv;
        long n = a + b + c + d;
        if (n == 0) return 0;
        double expectedAC = (double) (a + c) * (a + b) / n;
        double expectedBD = (double) (b + d) * (a + b) / n;
        double chiA = Math.pow(Math.abs(a - expectedAC) - 0.5, 2) / Math.max(expectedAC, 1);
        double chiB = Math.pow(Math.abs(d - expectedBD) - 0.5, 2) / Math.max(expectedBD, 1);
        return chiA + chiB;
    }

    /**
     * χ² → p-value（df=1，用 erfc(√(χ²/2))）。
     */
    static double chiSquareToPValue(double chiSquare) {
        if (chiSquare <= 0) return 1.0;
        return erfc(Math.sqrt(chiSquare / 2));
    }

    /**
     * 余误差函数 erfc(x)（基于 Abramowitz-Stegun 7.1.26 erf 近似，erfc=1-erf，最大误差 1.2e-7）。
     * χ²→p-value 场景下 x=√(χ²/2)&gt;=0 恒成立，仅支持 x&gt;=0 足够。
     */
    static double erfc(double x) {
        double absX = Math.abs(x);
        double t = 1.0 / (1.0 + 0.3275911 * absX);
        // Abramowitz-Stegun 7.1.26 计算的是 erf(|x|)：erf = 1 - poly(t) * exp(-x²)
        double erf = 1.0 - ((((1.061405429 * t - 1.453152027) * t + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t * Math.exp(-absX * absX);
        // erfc(x) = 1 - erf(x)；erfc 是偶函数的补：erfc(-x)=2-erfc(x)
        return x >= 0 ? (1.0 - erf) : (1.0 + erf);
    }
}

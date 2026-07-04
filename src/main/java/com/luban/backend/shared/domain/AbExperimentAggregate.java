package com.luban.backend.shared.domain;

import com.luban.backend.shared.domain.event.DomainEvent;
import com.luban.backend.shared.entity.AbExperiment;
import com.luban.backend.shared.entity.AbVariant;
import com.luban.backend.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

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
     * 启动实验（draft→running）。
     *
     * @param pageHasRunningExperiment 该 page 是否已有 running 实验（跨聚合查询，Service 传入）
     */
    public void start(boolean pageHasRunningExperiment) {
        if (pageHasRunningExperiment) {
            throw new BusinessException(HttpStatus.CONFLICT, "EXPERIMENT_CONFLICT",
                    "该页面已有运行中的实验");
        }
        assertTransition(STATUS_DRAFT, STATUS_RUNNING);
        root.setStatus(STATUS_RUNNING);
        root.setStartAt(Instant.now());
    }

    /** 结束实验（running→ended）。 */
    public void end() {
        assertTransition(STATUS_RUNNING, STATUS_ENDED);
        root.setStatus(STATUS_ENDED);
        root.setEndAt(Instant.now());
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
     * 稳定哈希（FNV-1a 32-bit）。同输入同输出，跨进程稳定，用于分桶确定性。
     */
    public static long stableHash(String input) {
        long hash = 0x811c9dc5L;
        for (int i = 0; i < input.length(); i++) {
            hash ^= input.charAt(i);
            hash *= 0x01000193L;
            hash &= 0xFFFFFFFFL;   // 保持 32-bit
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
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INSUFFICIENT_VARIANTS", "至少需要 2 个变体");
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

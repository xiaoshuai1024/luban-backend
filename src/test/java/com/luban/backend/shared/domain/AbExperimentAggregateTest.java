package com.luban.backend.shared.domain;

import com.luban.backend.shared.domain.event.ExperimentEndedEvent;
import com.luban.backend.shared.domain.event.ExperimentStartedEvent;
import com.luban.backend.shared.entity.AbExperiment;
import com.luban.backend.shared.entity.AbVariant;
import com.luban.backend.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AbExperimentAggregate 单测（backend-ddd-refactor plan v2 T9）。
 *
 * <p>锁定真聚合根范式不变量：
 * <ul>
 *   <li>状态机 draft→running→ended，非法转换抛异常</li>
 *   <li>assertStartable：单页单 running（跨聚合查询 Service 传入 boolean）</li>
 *   <li>χ² 显著性计算（Yates 校正，p<0.05 显著）</li>
 *   <li>分桶：stableHash（FNV-1a）+ 加权 roll</li>
 * </ul>
 */
class AbExperimentAggregateTest {

    @Test
    void startTransitionsDraftToRunningWhenNoConflict() {
        AbExperiment exp = draftExperiment();
        AbExperimentAggregate agg = AbExperimentAggregate.reconstitute(exp, twoVariants());

        agg.start(false);   // 无冲突

        assertThat(agg.toExperiment().getStatus()).isEqualTo("running");
        assertThat(agg.toExperiment().getStartAt()).isNotNull();
        // G1 补：start 发 ExperimentStartedEvent（聚合根拥有状态机事件）
        assertThat(agg.pullEvents())
                .singleElement()
                .isInstanceOf(ExperimentStartedEvent.class);
    }

    @Test
    void startRejectsWhenPageAlreadyHasRunningExperiment() {
        AbExperiment exp = draftExperiment();
        AbExperimentAggregate agg = AbExperimentAggregate.reconstitute(exp, twoVariants());

        assertThatThrownBy(() -> agg.start(true))   // 单页单 running 冲突
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo("EXPERIMENT_CONFLICT");
    }

    @Test
    void startRejectsAlreadyRunning() {
        AbExperiment exp = draftExperiment();
        exp.setStatus("running");
        AbExperimentAggregate agg = AbExperimentAggregate.reconstitute(exp, twoVariants());

        assertThatThrownBy(() -> agg.start(false))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo("INVALID_STATE_TRANSITION");
    }

    @Test
    void endTransitionsRunningToEnded() {
        AbExperiment exp = draftExperiment();
        exp.setStatus("running");
        AbExperimentAggregate agg = AbExperimentAggregate.reconstitute(exp, twoVariants());

        agg.end();

        assertThat(agg.toExperiment().getStatus()).isEqualTo("ended");
        assertThat(agg.toExperiment().getEndAt()).isNotNull();
        // G1 补：end 发 ExperimentEndedEvent
        assertThat(agg.pullEvents())
                .singleElement()
                .isInstanceOf(ExperimentEndedEvent.class);
    }

    @Test
    void endRejectsDraft() {
        AbExperiment exp = draftExperiment();
        AbExperimentAggregate agg = AbExperimentAggregate.reconstitute(exp, twoVariants());

        assertThatThrownBy(() -> agg.end())
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo("INVALID_STATE_TRANSITION");
    }

    // === χ² 显著性（纯算法） ===

    @Test
    void computeSignificanceRejectsLessThanTwoVariants() {
        assertThatThrownBy(() -> AbExperimentAggregate.computeSignificance(
                new AbExperimentAggregate.VariantStats("v1", 100, 10, 0.1),
                null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo("INSUFFICIENT_VARIANTS");
    }

    @Test
    void computeSignificanceDetectsSignificantDifference() {
        // A: 1000 views, 50 conv (5%)；B: 1000 views, 100 conv (10%) → 显著差异
        var control = new AbExperimentAggregate.VariantStats("a", 1000, 50, 0.05);
        var experiment = new AbExperimentAggregate.VariantStats("b", 1000, 100, 0.10);

        var result = AbExperimentAggregate.computeSignificance(control, experiment);

        assertThat(result.isSignificant()).isTrue();
        assertThat(result.pValue()).isLessThan(0.05);
        assertThat(result.lift()).isCloseTo(100.0, within(0.1));   // (0.10-0.05)/0.05*100 = 100%
    }

    @Test
    void computeSignificanceReturnsNotSignificantForSimilarRates() {
        // A: 100/5, B: 100/6 → 差异小，不显著
        var control = new AbExperimentAggregate.VariantStats("a", 100, 5, 0.05);
        var experiment = new AbExperimentAggregate.VariantStats("b", 100, 6, 0.06);

        var result = AbExperimentAggregate.computeSignificance(control, experiment);

        assertThat(result.isSignificant()).isFalse();
        assertThat(result.pValue()).isGreaterThan(0.05);
    }

    // === 分桶（stableHash + 加权） ===

    @Test
    void stableHashIsDeterministicForSameInput() {
        // 同输入同输出（FNV-1a 确定性）
        int h1 = AbExperimentAggregate.stableHash("visitor-1:exp-1");
        int h2 = AbExperimentAggregate.stableHash("visitor-1:exp-1");
        assertThat(h1).isEqualTo(h2);
    }

    @Test
    void stableHashDiffersForDifferentInput() {
        int h1 = AbExperimentAggregate.stableHash("visitor-1:exp-1");
        int h2 = AbExperimentAggregate.stableHash("visitor-2:exp-1");
        assertThat(h1).isNotEqualTo(h2);
    }

    @Test
    void selectVariantByWeightRespectsControl() {
        AbVariant control = variant("A", true, 50);
        AbVariant exp = variant("B", false, 50);

        // roll < 50 → control (A)，roll >= 50 → experiment (B)
        assertThat(AbExperimentAggregate.selectVariantByWeight(List.of(control, exp), 0))
                .isEqualTo(control);
        assertThat(AbExperimentAggregate.selectVariantByWeight(List.of(control, exp), 49))
                .isEqualTo(control);
        assertThat(AbExperimentAggregate.selectVariantByWeight(List.of(control, exp), 50))
                .isEqualTo(exp);
        assertThat(AbExperimentAggregate.selectVariantByWeight(List.of(control, exp), 99))
                .isEqualTo(exp);
    }

    @Test
    void pullEventsDrains() {
        AbExperiment exp = draftExperiment();
        AbExperimentAggregate agg = AbExperimentAggregate.reconstitute(exp, twoVariants());
        assertThat(agg.pullEvents()).isEmpty();
    }

    private static AbExperiment draftExperiment() {
        AbExperiment exp = new AbExperiment();
        exp.setId("exp-1");
        exp.setSiteId("site-1");
        exp.setPageId("page-1");
        exp.setStatus("draft");
        exp.setTrafficPct(100);
        return exp;
    }

    private static List<AbVariant> twoVariants() {
        return List.of(variant("A", true, 50), variant("B", false, 50));
    }

    private static AbVariant variant(String label, boolean isControl, int weight) {
        AbVariant v = new AbVariant();
        v.setId(label.toLowerCase());
        v.setExperimentId("exp-1");
        v.setLabel(label);
        v.setWeight(weight);
        v.setControl(isControl);
        return v;
    }

    private static org.assertj.core.data.Offset<Double> within(double tolerance) {
        return org.assertj.core.data.Offset.offset(tolerance);
    }
}

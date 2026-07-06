package com.luban.backend.operatorside.service;

import com.luban.backend.shared.domain.AbExperimentAggregate;
import com.luban.backend.shared.entity.AbAssignment;
import com.luban.backend.shared.entity.AbExperiment;
import com.luban.backend.shared.entity.AbVariant;
import com.luban.backend.shared.entity.AnalyticsDaily;
import com.luban.backend.shared.exception.BusinessException;
import com.luban.backend.shared.repository.AbExperimentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AbService 单测（backend-ddd-refactor plan v2 T9/10/11）。
 *
 * <p>覆盖 CRUD（含单页单 running 冲突 EXPERIMENT_CONFLICT）、一致性哈希分桶（命中/未命中流量/幂等命中/
 * 无 variant 兜底）、χ² 显著性（显著性差异 + 变体不足 INSUFFICIENT_VARIANTS + 对照选取 fallback）。
 * v2：mock 改为 {@link AbExperimentRepository} + AbAssignmentMapper + AnalyticsDailyMapper
 * （领域 Mapper 全部封装在 Repository，零直接依赖）。
 */
@ExtendWith(MockitoExtension.class)
class AbServiceTest {

    @Mock private AbExperimentRepository experimentRepository;
    @Mock private com.luban.backend.shared.support.DomainEventPublisher eventPublisher;

    private AbService service;

    @BeforeEach
    void setUp() {
        service = new AbService(experimentRepository, eventPublisher);
    }

    // ===== listExperiments =====

    @Test
    void listExperimentsReturnsRepositoryResult() {
        AbExperiment exp = new AbExperiment();
        exp.setId("exp-1");
        when(experimentRepository.listBySiteId("site-1", "running")).thenReturn(List.of(exp));

        List<AbExperiment> result = service.listExperiments("site-1", "running");

        assertThat(result).extracting(AbExperiment::getId).containsExactly("exp-1");
        verify(experimentRepository).listBySiteId("site-1", "running");
    }

    @Test
    void listExperimentsWithNullStatusPassesThrough() {
        when(experimentRepository.listBySiteId("site-1", null)).thenReturn(List.of());

        List<AbExperiment> result = service.listExperiments("site-1", null);

        assertThat(result).isEmpty();
    }

    // ===== getExperimentDetail =====

    @Test
    void getExperimentDetailReturnsExperimentAndVariants() {
        AbExperiment exp = new AbExperiment();
        exp.setId("exp-1");
        AbVariant v = new AbVariant();
        v.setId("var-1");
        when(experimentRepository.findById("exp-1"))
                .thenReturn(Optional.of(AbExperimentAggregate.reconstitute(exp, List.of(v))));

        AbService.AbExperimentDetail detail = service.getExperimentDetail("exp-1");

        assertThat(detail.experiment().getId()).isEqualTo("exp-1");
        assertThat(detail.variants()).extracting(AbVariant::getId).containsExactly("var-1");
    }

    @Test
    void getExperimentDetailThrowsWhenNotFound() {
        when(experimentRepository.findById("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getExperimentDetail("nope"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo("EXPERIMENT_NOT_FOUND");
    }

    // ===== createExperiment =====

    @Test
    void createRunningExperimentInsertsExperimentAndVariants() {
        AbService.CreateExperimentInput input = new AbService.CreateExperimentInput(
                "site-1", "page-1", "Landing Test", "running", 80,
                List.of(new AbService.VariantInput("A", "pv-a", 50, true),
                        new AbService.VariantInput("B", "pv-b", 50, false)));
        when(experimentRepository.findRunningByPageId("page-1")).thenReturn(null);

        AbExperiment created = service.createExperiment(input);

        assertThat(created.getSiteId()).isEqualTo("site-1");
        assertThat(created.getPageId()).isEqualTo("page-1");
        assertThat(created.getStatus()).isEqualTo("running");
        assertThat(created.getTrafficPct()).isEqualTo(80);
        assertThat(created.getStartAt()).isNotNull();

        ArgumentCaptor<AbExperimentAggregate> aggCaptor = ArgumentCaptor.forClass(AbExperimentAggregate.class);
        verify(experimentRepository).save(aggCaptor.capture());
        AbExperimentAggregate saved = aggCaptor.getValue();
        assertThat(saved.toExperiment().getStatus()).isEqualTo("running");
        assertThat(saved.variants()).hasSize(2);
        AbVariant v0 = saved.variants().get(0);
        assertThat(v0.getLabel()).isEqualTo("A");
        assertThat(v0.getWeight()).isEqualTo(50);
        assertThat(v0.isControl()).isTrue();
        assertThat(v0.getExperimentId()).isEqualTo(created.getId());
        assertThat(saved.variants().get(1).isControl()).isFalse();
    }

    @Test
    void createRunningExperimentThrowsWhenPageAlreadyHasRunning() {
        AbExperiment existing = new AbExperiment();
        existing.setId("exp-old");
        existing.setName("旧实验");
        when(experimentRepository.findRunningByPageId("page-1")).thenReturn(existing);

        AbService.CreateExperimentInput input = new AbService.CreateExperimentInput(
                "site-1", "page-1", "New", "running", 100,
                List.of(new AbService.VariantInput("A", "pv-a", 50, true)));

        assertThatThrownBy(() -> service.createExperiment(input))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo("EXPERIMENT_CONFLICT");

        verify(experimentRepository, never()).save(any());
    }

    @Test
    void createDraftExperimentSkipsConflictCheck() {
        // draft 状态不触发 findRunningByPageId（仅在 status=running 时校验）
        AbService.CreateExperimentInput input = new AbService.CreateExperimentInput(
                "site-1", "page-1", "Draft", "draft", null,
                List.of(new AbService.VariantInput("A", "pv-a", null, null)));

        AbExperiment created = service.createExperiment(input);

        assertThat(created.getStatus()).isEqualTo("draft");
        assertThat(created.getTrafficPct()).isEqualTo(100);     // null 默认 100
        assertThat(created.getStartAt()).isNull();              // draft 不设 startAt
        verify(experimentRepository, never()).findRunningByPageId(anyString());

        ArgumentCaptor<AbExperimentAggregate> aggCaptor = ArgumentCaptor.forClass(AbExperimentAggregate.class);
        verify(experimentRepository).save(aggCaptor.capture());
        // weight null → 默认 50；isControl null → false
        AbVariant inserted = aggCaptor.getValue().variants().get(0);
        assertThat(inserted.getWeight()).isEqualTo(50);
        assertThat(inserted.isControl()).isFalse();
    }

    @Test
    void createExperimentWithEmptyVariantsDoesNotFail() {
        AbService.CreateExperimentInput input = new AbService.CreateExperimentInput(
                "site-1", "page-1", "No variants", "draft", 100, List.of());

        AbExperiment created = service.createExperiment(input);

        assertThat(created.getId()).isNotNull();
        verify(experimentRepository).save(any());
    }

    // ===== endExperiment =====

    @Test
    void endExperimentSetsStatusEndedAndPersists() {
        AbExperiment exp = new AbExperiment();
        exp.setId("exp-1");
        exp.setStatus("running");
        when(experimentRepository.findById("exp-1"))
                .thenReturn(Optional.of(AbExperimentAggregate.reconstitute(exp, List.of())));

        AbExperiment ended = service.endExperiment("exp-1");

        assertThat(ended.getStatus()).isEqualTo("ended");
        assertThat(ended.getEndAt()).isNotNull();
        verify(experimentRepository).save(any());
    }

    @Test
    void endExperimentThrowsWhenNotFound() {
        when(experimentRepository.findById("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.endExperiment("nope"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo("EXPERIMENT_NOT_FOUND");

        verify(experimentRepository, never()).save(any());
    }

    @Test
    void endExperimentThrowsWhenInvalidTransition() {
        // draft → ended 非法转换（聚合根状态机守护）
        AbExperiment exp = new AbExperiment();
        exp.setId("exp-1");
        exp.setStatus("draft");
        when(experimentRepository.findById("exp-1"))
                .thenReturn(Optional.of(AbExperimentAggregate.reconstitute(exp, List.of())));

        assertThatThrownBy(() -> service.endExperiment("exp-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo("INVALID_STATE_TRANSITION");

        verify(experimentRepository, never()).save(any());
    }

    // ===== assignVariant =====

    @Test
    void assignVariantReturnsNullWhenNoRunningExperiment() {
        when(experimentRepository.findRunningByPageId("page-1")).thenReturn(null);

        AbService.AssignResult result = service.assignVariant("visitor-1", "page-1");

        assertThat(result.experimentId()).isNull();
        assertThat(result.variantId()).isNull();
        assertThat(result.inExperiment()).isFalse();
    }

    @Test
    void assignVariantReturnsNotInExperimentWhenTrafficPctIsZero() {
        // trafficPct=0 时，hash >= 0 恒成立 → 未命中实验流量
        AbExperiment exp = new AbExperiment();
        exp.setId("exp-1");
        exp.setTrafficPct(0);
        when(experimentRepository.findRunningByPageId("page-1")).thenReturn(exp);

        AbService.AssignResult result = service.assignVariant("visitor-1", "page-1");

        assertThat(result.inExperiment()).isFalse();
        assertThat(result.variantId()).isNull();
        verify(experimentRepository, never()).getAssignment(anyString(), anyString());
        verify(experimentRepository, never()).saveAssignment(any());
    }

    @Test
    void assignVariantReturnsExistingAssignmentWhenAlreadyBucketed() {
        AbExperiment exp = new AbExperiment();
        exp.setId("exp-1");
        exp.setTrafficPct(100);     // 全量流量必命中
        when(experimentRepository.findRunningByPageId("page-1")).thenReturn(exp);
        AbAssignment existing = new AbAssignment();
        existing.setVariantId("var-prev");
        when(experimentRepository.getAssignment("visitor-1", "exp-1")).thenReturn(existing);

        AbService.AssignResult result = service.assignVariant("visitor-1", "page-1");

        assertThat(result.inExperiment()).isTrue();
        assertThat(result.experimentId()).isEqualTo("exp-1");
        assertThat(result.variantId()).isEqualTo("var-prev");   // 幂等返回已有分桶
        verify(experimentRepository, never()).listVariantsByExperimentId(anyString());
        verify(experimentRepository, never()).saveAssignment(any());        // 已存在不重复插入
    }

    @Test
    void assignVariantSelectsVariantByWeightAndRecordsAssignment() {
        AbExperiment exp = new AbExperiment();
        exp.setId("exp-1");
        exp.setTrafficPct(100);
        when(experimentRepository.findRunningByPageId("page-1")).thenReturn(exp);
        when(experimentRepository.getAssignment("visitor-1", "exp-1")).thenReturn(null);
        // A weight=100（吃掉所有 roll），B weight=0（永不命中）→ 必选 A
        AbVariant a = newVariant("var-a", "A", 100, true);
        AbVariant b = newVariant("var-b", "B", 0, false);
        when(experimentRepository.listVariantsByExperimentId("exp-1")).thenReturn(List.of(a, b));

        AbService.AssignResult result = service.assignVariant("visitor-1", "page-1");

        assertThat(result.inExperiment()).isTrue();
        assertThat(result.experimentId()).isEqualTo("exp-1");
        assertThat(result.variantId()).isEqualTo("var-a");

        ArgumentCaptor<AbAssignment> captor = ArgumentCaptor.forClass(AbAssignment.class);
        verify(experimentRepository).saveAssignment(captor.capture());
        AbAssignment saved = captor.getValue();
        assertThat(saved.getVisitorId()).isEqualTo("visitor-1");
        assertThat(saved.getExperimentId()).isEqualTo("exp-1");
        assertThat(saved.getVariantId()).isEqualTo("var-a");
        assertThat(saved.getAssignedAt()).isNotNull();
    }

    @Test
    void assignVariantReturnsNotInExperimentWhenNoVariantsConfigured() {
        AbExperiment exp = new AbExperiment();
        exp.setId("exp-1");
        exp.setTrafficPct(100);
        when(experimentRepository.findRunningByPageId("page-1")).thenReturn(exp);
        when(experimentRepository.getAssignment("visitor-1", "exp-1")).thenReturn(null);
        when(experimentRepository.listVariantsByExperimentId("exp-1")).thenReturn(List.of());

        AbService.AssignResult result = service.assignVariant("visitor-1", "page-1");

        assertThat(result.inExperiment()).isFalse();
        verify(experimentRepository, never()).saveAssignment(any());
    }

    @Test
    void assignVariantReturnsNotInExperimentWhenTotalWeightNonPositive() {
        AbExperiment exp = new AbExperiment();
        exp.setId("exp-1");
        exp.setTrafficPct(100);
        when(experimentRepository.findRunningByPageId("page-1")).thenReturn(exp);
        when(experimentRepository.getAssignment("visitor-1", "exp-1")).thenReturn(null);
        AbVariant v = newVariant("var-a", "A", 0, true);
        when(experimentRepository.listVariantsByExperimentId("exp-1")).thenReturn(List.of(v));

        AbService.AssignResult result = service.assignVariant("visitor-1", "page-1");

        assertThat(result.inExperiment()).isFalse();
        verify(experimentRepository, never()).saveAssignment(any());
    }

    // ===== computeSignificance =====

    @Test
    void computeSignificanceThrowsWhenInsufficientVariants() {
        AbExperiment exp = new AbExperiment();
        exp.setId("exp-1");
        when(experimentRepository.findById("exp-1"))
                .thenReturn(Optional.of(AbExperimentAggregate.reconstitute(exp,
                        List.of(newVariant("var-a", "A", 50, true)))));

        assertThatThrownBy(() -> service.computeSignificance("exp-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo("INSUFFICIENT_VARIANTS");
    }

    @Test
    void computeSignificanceDetectsSignificantDifference() {
        // 对照转化率 10%（1000 views / 100 conv），实验组转化率 20%（1000/200）→ p<0.05 显著
        AbExperiment exp = new AbExperiment();
        exp.setId("exp-1");
        exp.setSiteId("site-1");
        when(experimentRepository.findById("exp-1"))
                .thenReturn(Optional.of(AbExperimentAggregate.reconstitute(exp,
                        List.of(newVariant("var-a", "A", 50, true),
                                newVariant("var-b", "B", 50, false)))));
        when(experimentRepository.aggregateDailyByVariant(eq("site-1"), eq("var-a")))
                .thenReturn(new long[]{1000, 100});
        when(experimentRepository.aggregateDailyByVariant(eq("site-1"), eq("var-b")))
                .thenReturn(new long[]{1000, 200});

        AbService.SignificanceResult result = service.computeSignificance("exp-1");

        assertThat(result.experimentId()).isEqualTo("exp-1");
        assertThat(result.control().variantId()).isEqualTo("var-a");        // isControl=true 被选为对照
        assertThat(result.experiment().variantId()).isEqualTo("var-b");
        assertThat(result.control().views()).isEqualTo(1000);
        assertThat(result.control().conversions()).isEqualTo(100);
        assertThat(result.control().rate()).isEqualTo(0.1);
        assertThat(result.experiment().rate()).isEqualTo(0.2);
        assertThat(result.chiSquare()).isPositive();
        assertThat(result.pValue()).isLessThan(0.05);
        assertThat(result.isSignificant()).isTrue();
        assertThat(result.lift()).isEqualTo(100.0);      // (0.2-0.1)/0.1 * 100
    }

    @Test
    void computeSignificanceReportsNonSignificantForSimilarRates() {
        // 两组转化率几乎相同（1000/100 vs 1000/105）→ 不显著
        AbExperiment exp = new AbExperiment();
        exp.setId("exp-1");
        exp.setSiteId("site-1");
        when(experimentRepository.findById("exp-1"))
                .thenReturn(Optional.of(AbExperimentAggregate.reconstitute(exp,
                        List.of(newVariant("var-a", "A", 50, true),
                                newVariant("var-b", "B", 50, false)))));
        when(experimentRepository.aggregateDailyByVariant(eq("site-1"), eq("var-a")))
                .thenReturn(new long[]{1000, 100});
        when(experimentRepository.aggregateDailyByVariant(eq("site-1"), eq("var-b")))
                .thenReturn(new long[]{1000, 105});

        AbService.SignificanceResult result = service.computeSignificance("exp-1");

        assertThat(result.pValue()).isGreaterThan(0.05);
        assertThat(result.isSignificant()).isFalse();
    }

    @Test
    void computeSignificanceFallsBackToFirstVariantWhenNoControlFlag() {
        // 无 isControl=true 时，取 variants.get(0) 作为对照
        AbExperiment exp = new AbExperiment();
        exp.setId("exp-1");
        exp.setSiteId("site-1");
        when(experimentRepository.findById("exp-1"))
                .thenReturn(Optional.of(AbExperimentAggregate.reconstitute(exp,
                        List.of(newVariant("var-a", "A", 50, false),     // 非 control
                                newVariant("var-b", "B", 50, false)))));
        when(experimentRepository.aggregateDailyByVariant(eq("site-1"), eq("var-a")))
                .thenReturn(new long[]{100, 10});
        when(experimentRepository.aggregateDailyByVariant(eq("site-1"), eq("var-b")))
                .thenReturn(new long[]{100, 12});

        AbService.SignificanceResult result = service.computeSignificance("exp-1");

        // a 作为 fallback 对照，b 作为实验组（与 a id 不同）
        assertThat(result.control().variantId()).isEqualTo("var-a");
        assertThat(result.experiment().variantId()).isEqualTo("var-b");
    }

    @Test
    void computeSignificanceZeroViewsYieldsZeroRateAndNonPositiveLift() {
        AbExperiment exp = new AbExperiment();
        exp.setId("exp-1");
        exp.setSiteId("site-1");
        when(experimentRepository.findById("exp-1"))
                .thenReturn(Optional.of(AbExperimentAggregate.reconstitute(exp,
                        List.of(newVariant("var-a", "A", 50, true),
                                newVariant("var-b", "B", 50, false)))));
        // 对照 0 views → controlRate=0；lift 分支 controlRate>0 不成立 → lift=0
        when(experimentRepository.aggregateDailyByVariant(eq("site-1"), eq("var-a")))
                .thenReturn(new long[]{0, 0});
        when(experimentRepository.aggregateDailyByVariant(eq("site-1"), eq("var-b")))
                .thenReturn(new long[]{0, 0});

        AbService.SignificanceResult result = service.computeSignificance("exp-1");

        assertThat(result.control().rate()).isZero();
        assertThat(result.experiment().rate()).isZero();
        assertThat(result.lift()).isZero();
        // n=0 → chiSquare=0 → pValue=1
        assertThat(result.chiSquare()).isZero();
        assertThat(result.pValue()).isEqualTo(1.0);
        assertThat(result.isSignificant()).isFalse();
    }

    // ===== helpers =====

    private AbVariant newVariant(String id, String label, int weight, boolean isControl) {
        AbVariant v = new AbVariant();
        v.setId(id);
        v.setLabel(label);
        v.setWeight(weight);
        v.setControl(isControl);
        return v;
    }

    private AnalyticsDaily daily(String variantId, long views, long conversions) {
        AnalyticsDaily d = new AnalyticsDaily();
        d.setVariantId(variantId);
        d.setViews(views);
        d.setConversions(conversions);
        return d;
    }
}

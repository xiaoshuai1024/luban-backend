package com.luban.backend.operatorside.service;

import com.luban.backend.shared.dto.PlanResponse;
import com.luban.backend.shared.entity.Plan;
import com.luban.backend.shared.mapper.PlanMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * PlanService 单测（backend-ddd-refactor plan v2 T16，补覆盖率）。
 *
 * <p>覆盖 listPlans（map + gates 解析）/ getPlan（透传）/ parseGates（null/blank/正常/异常 4 分支）/
 * isGateEnabled（plan null/含 gate/不含 gate）。
 */
@ExtendWith(MockitoExtension.class)
class PlanServiceTest {

    @Mock private PlanMapper planMapper;

    private PlanService service;

    @BeforeEach
    void setUp() {
        service = new PlanService(planMapper);
    }

    private Plan plan(String code, String gatesJson) {
        Plan p = new Plan();
        p.setPlanCode(code);
        p.setName(code + " 套餐");
        p.setGates(gatesJson);
        p.setSortOrder(1);
        return p;
    }

    @Test
    void parseGates_returns_empty_for_null() {
        assertThat(service.parseGates(null)).isEmpty();
    }

    @Test
    void parseGates_returns_empty_for_blank() {
        assertThat(service.parseGates("   ")).isEmpty();
    }

    @Test
    void parseGates_parses_valid_json() {
        List<String> gates = service.parseGates("[\"analytics\",\"ab_testing\"]");
        assertThat(gates).containsExactly("analytics", "ab_testing");
    }

    @Test
    void parseGates_returns_empty_for_invalid_json() {
        assertThat(service.parseGates("not-json{")).isEmpty();
    }

    @Test
    void listPlans_maps_to_response_with_parsed_gates() {
        Plan p = plan("pro", "[\"analytics\"]");
        when(planMapper.listAll()).thenReturn(List.of(p));

        List<PlanResponse> result = service.listPlans();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).planCode()).isEqualTo("pro");
        assertThat(result.get(0).gates()).containsExactly("analytics");
    }

    @Test
    void getPlan_returns_mapper_result() {
        Plan p = plan("free", "[]");
        when(planMapper.getByCode("free")).thenReturn(p);

        Plan result = service.getPlan("free");

        assertThat(result.getPlanCode()).isEqualTo("free");
    }

    @Test
    void isGateEnabled_returns_true_when_plan_contains_gate() {
        when(planMapper.getByCode("pro")).thenReturn(plan("pro", "[\"analytics\",\"ab_testing\"]"));

        assertThat(service.isGateEnabled("pro", "analytics")).isTrue();
        assertThat(service.isGateEnabled("pro", "ab_testing")).isTrue();
    }

    @Test
    void isGateEnabled_returns_false_when_plan_does_not_contain_gate() {
        when(planMapper.getByCode("free")).thenReturn(plan("free", "[]"));

        assertThat(service.isGateEnabled("free", "analytics")).isFalse();
    }

    @Test
    void isGateEnabled_returns_false_when_plan_not_found() {
        when(planMapper.getByCode("ghost")).thenReturn(null);

        assertThat(service.isGateEnabled("ghost", "analytics")).isFalse();
    }
}

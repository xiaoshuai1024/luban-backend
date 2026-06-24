package com.luban.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luban.backend.dto.PlanResponse;
import com.luban.backend.entity.Plan;
import com.luban.backend.mapper.PlanMapper;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * Plan 服务（v02 billing 域）。套餐定义只读（seed 初始化），提供列表 + 单查 + gates 解析。
 */
@Service
public class PlanService {

    private final PlanMapper planMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PlanService(PlanMapper planMapper) {
        this.planMapper = planMapper;
    }

    /** 套餐列表（按 sort_order）。gates JSON 解析为 List<String>。 */
    public List<PlanResponse> listPlans() {
        return planMapper.listAll().stream()
                .map(p -> PlanResponse.fromEntity(p, parseGates(p.getGates())))
                .toList();
    }

    public Plan getPlan(String planCode) {
        return planMapper.getByCode(planCode);
    }

    /** 解析 plan.gates JSON 字符串为 List<String>；空/异常返回空列表。 */
    public List<String> parseGates(String gatesJson) {
        if (gatesJson == null || gatesJson.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(gatesJson, new TypeReference<>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /** 判断某 plan 是否放行某 gate_key。 */
    public boolean isGateEnabled(String planCode, String gateKey) {
        Plan plan = planMapper.getByCode(planCode);
        if (plan == null) return false;
        return parseGates(plan.getGates()).contains(gateKey);
    }
}

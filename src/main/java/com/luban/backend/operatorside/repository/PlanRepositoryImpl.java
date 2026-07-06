package com.luban.backend.operatorside.repository;

import com.luban.backend.shared.entity.Plan;
import com.luban.backend.shared.mapper.PlanMapper;
import com.luban.backend.shared.repository.PlanRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Plan 仓储实现：封装 {@link PlanMapper}。
 * 套餐定义只读（seed 初始化）；取消原 PlanMapper 白名单后所有读取经此。
 */
@Repository
public class PlanRepositoryImpl implements PlanRepository {

    private final PlanMapper planMapper;

    public PlanRepositoryImpl(PlanMapper planMapper) {
        this.planMapper = planMapper;
    }

    @Override
    public List<Plan> listAll() {
        return planMapper.listAll();
    }

    @Override
    public Optional<Plan> getByCode(String planCode) {
        return Optional.ofNullable(planMapper.getByCode(planCode));
    }
}

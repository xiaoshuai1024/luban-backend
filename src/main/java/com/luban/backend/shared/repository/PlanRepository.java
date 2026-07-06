package com.luban.backend.shared.repository;

import com.luban.backend.shared.entity.Plan;

import java.util.List;
import java.util.Optional;

/**
 * Plan 仓储接口（domain 抽象，不依赖 MyBatis）。
 *
 * <p>套餐定义只读（由 schema.sql seed 初始化 free/starter/growth）。
 * 取消原 {@code PlanMapper} 白名单后，所有套餐读取统一经此接口。
 */
public interface PlanRepository {

    List<Plan> listAll();

    Optional<Plan> getByCode(String planCode);
}

package com.luban.backend.shared.repository;

import com.luban.backend.shared.entity.FeatureGate;

import java.util.Optional;

/**
 * FeatureGate 仓储接口（domain 抽象，不依赖 MyBatis）。
 *
 * <p>特性开关按 siteId+key 读写；未配置视为默认开启（由 Service 层兜底）。
 */
public interface FeatureGateRepository {

    Optional<FeatureGate> findBySiteAndKey(String siteId, String gateKey);

    /** upsert：存在则更新 enabled/updatedAt，不存在则插入。 */
    void upsert(FeatureGate gate);
}

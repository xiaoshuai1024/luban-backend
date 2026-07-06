package com.luban.backend.operatorside.repository;

import com.luban.backend.shared.entity.FeatureGate;
import com.luban.backend.shared.mapper.FeatureGateMapper;
import com.luban.backend.shared.repository.FeatureGateRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * FeatureGate 仓储实现：封装 {@link FeatureGateMapper}。
 */
@Repository
public class FeatureGateRepositoryImpl implements FeatureGateRepository {

    private final FeatureGateMapper featureGateMapper;

    public FeatureGateRepositoryImpl(FeatureGateMapper featureGateMapper) {
        this.featureGateMapper = featureGateMapper;
    }

    @Override
    public Optional<FeatureGate> findBySiteAndKey(String siteId, String gateKey) {
        return Optional.ofNullable(featureGateMapper.getBySiteAndKey(siteId, gateKey));
    }

    @Override
    public void upsert(FeatureGate gate) {
        featureGateMapper.upsert(gate);
    }
}

package com.luban.backend.operatorside.repository;

import com.luban.backend.shared.domain.AbExperimentAggregate;
import com.luban.backend.shared.entity.AbExperiment;
import com.luban.backend.shared.entity.AbVariant;
import com.luban.backend.shared.mapper.AbExperimentMapper;
import com.luban.backend.shared.mapper.AbVariantMapper;
import com.luban.backend.shared.repository.AbExperimentRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * AB 实验仓储实现（backend-ddd-refactor plan v2 T9）。
 * save：insert（含变体 batchInsert）或 updateStatus（聚合根 start/end 仅触及 status/时间戳列）。
 */
@Repository
public class AbExperimentRepositoryImpl implements AbExperimentRepository {

    private final AbExperimentMapper experimentMapper;
    private final AbVariantMapper variantMapper;

    public AbExperimentRepositoryImpl(AbExperimentMapper experimentMapper,
                                      AbVariantMapper variantMapper) {
        this.experimentMapper = experimentMapper;
        this.variantMapper = variantMapper;
    }

    @Override
    public Optional<AbExperimentAggregate> findById(String experimentId) {
        AbExperiment exp = experimentMapper.getById(experimentId);
        if (exp == null) return Optional.empty();
        List<AbVariant> variants = variantMapper.listByExperiment(experimentId);
        return Optional.of(AbExperimentAggregate.reconstitute(exp, variants));
    }

    @Override
    public List<AbExperiment> listBySiteId(String siteId, String status) {
        return experimentMapper.listBySite(siteId, status);
    }

    @Override
    public AbExperiment findRunningByPageId(String pageId) {
        return experimentMapper.findRunningByPage(pageId);
    }

    @Override
    public List<AbVariant> listVariantsByExperimentId(String experimentId) {
        return variantMapper.listByExperiment(experimentId);
    }

    @Override
    public void save(AbExperimentAggregate aggregate) {
        AbExperiment entity = aggregate.toExperiment();
        if (experimentMapper.getById(entity.getId()) == null) {
            experimentMapper.insert(entity);
            List<AbVariant> variants = aggregate.variants();
            if (variants != null && !variants.isEmpty()) {
                variantMapper.batchInsert(variants);
            }
        } else {
            experimentMapper.updateStatus(entity);
        }
    }
}

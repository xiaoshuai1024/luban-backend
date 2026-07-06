package com.luban.backend.operatorside.repository;

import com.luban.backend.shared.domain.AbExperimentAggregate;
import com.luban.backend.shared.entity.AbAssignment;
import com.luban.backend.shared.entity.AbExperiment;
import com.luban.backend.shared.entity.AbVariant;
import com.luban.backend.shared.entity.AnalyticsDaily;
import com.luban.backend.shared.mapper.AbAssignmentMapper;
import com.luban.backend.shared.mapper.AbExperimentMapper;
import com.luban.backend.shared.mapper.AbVariantMapper;
import com.luban.backend.shared.mapper.AnalyticsDailyMapper;
import com.luban.backend.shared.repository.AbExperimentRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * AB 实验仓储实现（backend-ddd-refactor plan v2 T9）。
 * save：insert（含变体 batchInsert）或 updateStatus（聚合根 start/end 仅触及 status/时间戳列）。
 *
 * <p>G1 补：AbAssignment + AnalyticsDaily 经此封装，AbService 零 Mapper 依赖。
 */
@Repository
public class AbExperimentRepositoryImpl implements AbExperimentRepository {

    private final AbExperimentMapper experimentMapper;
    private final AbVariantMapper variantMapper;
    private final AbAssignmentMapper assignmentMapper;
    private final AnalyticsDailyMapper dailyMapper;

    public AbExperimentRepositoryImpl(AbExperimentMapper experimentMapper,
                                      AbVariantMapper variantMapper,
                                      AbAssignmentMapper assignmentMapper,
                                      AnalyticsDailyMapper dailyMapper) {
        this.experimentMapper = experimentMapper;
        this.variantMapper = variantMapper;
        this.assignmentMapper = assignmentMapper;
        this.dailyMapper = dailyMapper;
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
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
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

    @Override
    public AbAssignment getAssignment(String visitorId, String experimentId) {
        return assignmentMapper.get(visitorId, experimentId);
    }

    @Override
    public void saveAssignment(AbAssignment assignment) {
        assignmentMapper.insert(assignment);
    }

    @Override
    public List<AnalyticsDaily> listDailyBySiteAndDateRange(String siteId, LocalDate from, LocalDate to) {
        return dailyMapper.listBySiteAndDateRange(siteId, from, to);
    }

    @Override
    public long[] aggregateDailyByVariant(String siteId, String variantId) {
        return dailyMapper.aggregateByVariant(siteId, variantId);
    }
}

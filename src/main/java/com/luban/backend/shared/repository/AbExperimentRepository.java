package com.luban.backend.shared.repository;

import com.luban.backend.shared.domain.AbExperimentAggregate;
import com.luban.backend.shared.entity.AbExperiment;
import com.luban.backend.shared.entity.AbVariant;

import java.util.List;
import java.util.Optional;

/**
 * AB 实验仓储接口（backend-ddd-refactor plan v2 T9）。
 * 封装 AbExperimentMapper + AbVariantMapper，AbService 零领域 Mapper 依赖。
 */
public interface AbExperimentRepository {
    Optional<AbExperimentAggregate> findById(String experimentId);
    List<AbExperiment> listBySiteId(String siteId, String status);
    AbExperiment findRunningByPageId(String pageId);
    List<AbVariant> listVariantsByExperimentId(String experimentId);
    void save(AbExperimentAggregate aggregate);
}

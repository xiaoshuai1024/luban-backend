package com.luban.backend.shared.repository;

import com.luban.backend.shared.entity.PageVersion;

import java.util.List;
import java.util.Optional;

/**
 * PageVersion 仓储接口（domain 抽象，不依赖 MyBatis）。
 *
 * <p>PageVersion 是 Page 聚合下的版本快照子实体（非独立聚合根），故直接返回 entity。
 */
public interface PageVersionRepository {

    /** 列表（不含 schema_json，列表轻量）。 */
    List<PageVersion> listByPageId(String pageId);

    Optional<PageVersion> getByIdAndPageId(String versionId, String pageId);

    int maxVersionNo(String pageId);

    void insert(PageVersion version);

    void deleteOlderThan(String pageId, int keepFromVersion);
}

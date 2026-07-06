package com.luban.backend.operatorside.repository;

import com.luban.backend.shared.entity.PageVersion;
import com.luban.backend.shared.mapper.PageVersionMapper;
import com.luban.backend.shared.repository.PageVersionRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * PageVersion 仓储实现：封装 {@link PageVersionMapper}。
 * PageVersion 为 Page 聚合下的版本快照子实体。
 */
@Repository
public class PageVersionRepositoryImpl implements PageVersionRepository {

    private final PageVersionMapper versionMapper;

    public PageVersionRepositoryImpl(PageVersionMapper versionMapper) {
        this.versionMapper = versionMapper;
    }

    @Override
    public List<PageVersion> listByPageId(String pageId) {
        return versionMapper.listByPageId(pageId);
    }

    @Override
    public Optional<PageVersion> getByIdAndPageId(String versionId, String pageId) {
        return Optional.ofNullable(versionMapper.getByIdAndPageId(versionId, pageId));
    }

    @Override
    public int maxVersionNo(String pageId) {
        return versionMapper.maxVersionNo(pageId);
    }

    @Override
    public void insert(PageVersion version) {
        versionMapper.insert(version);
    }

    @Override
    public void deleteOlderThan(String pageId, int keepFromVersion) {
        versionMapper.deleteOlderThan(pageId, keepFromVersion);
    }
}

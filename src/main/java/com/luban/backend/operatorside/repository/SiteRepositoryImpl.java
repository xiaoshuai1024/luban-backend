package com.luban.backend.operatorside.repository;

import com.luban.backend.shared.domain.SiteAggregate;
import com.luban.backend.shared.entity.Site;
import com.luban.backend.shared.mapper.SiteMapper;
import com.luban.backend.shared.repository.SiteRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 站点仓储实现（backend-ddd-refactor plan v2 T5）。
 * 封装 {@link SiteMapper}。save 按 getById 判 insert/update（对齐 FormRepositoryImpl 范式）。
 */
@Repository
public class SiteRepositoryImpl implements SiteRepository {

    private final SiteMapper siteMapper;

    public SiteRepositoryImpl(SiteMapper siteMapper) {
        this.siteMapper = siteMapper;
    }

    @Override
    public Optional<SiteAggregate> findById(String id) {
        Site s = siteMapper.getById(id);
        return Optional.ofNullable(s).map(SiteAggregate::reconstitute);
    }

    @Override
    public Optional<SiteAggregate> findBySlug(String slug) {
        Site s = siteMapper.getBySlug(slug);
        return Optional.ofNullable(s).map(SiteAggregate::reconstitute);
    }

    @Override
    public List<Site> list() {
        return siteMapper.list();
    }

    @Override
    public void save(SiteAggregate aggregate) {
        Site entity = aggregate.toSite();
        if (siteMapper.getById(entity.getId()) == null) {
            siteMapper.insert(entity);
        } else {
            siteMapper.update(entity);
        }
    }

    @Override
    public void deleteById(String id) {
        siteMapper.deleteById(id);
    }
}

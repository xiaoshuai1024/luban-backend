package com.luban.backend.operatorside.repository;

import com.luban.backend.shared.domain.TemplateAggregate;
import com.luban.backend.shared.entity.Template;
import com.luban.backend.shared.entity.TemplateVersion;
import com.luban.backend.shared.mapper.TemplateInstallationMapper;
import com.luban.backend.shared.mapper.TemplateMapper;
import com.luban.backend.shared.mapper.TemplateVersionMapper;
import com.luban.backend.shared.repository.TemplateRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 模板仓储实现（backend-ddd-refactor plan v2 T14）。
 * 封装 3 个 Mapper。save 处理 pendingNewVersion（create v1 / updateSchema 新版本）。
 */
@Repository
public class TemplateRepositoryImpl implements TemplateRepository {

    private final TemplateMapper templateMapper;
    private final TemplateVersionMapper versionMapper;
    private final TemplateInstallationMapper installationMapper;

    public TemplateRepositoryImpl(TemplateMapper templateMapper,
                                  TemplateVersionMapper versionMapper,
                                  TemplateInstallationMapper installationMapper) {
        this.templateMapper = templateMapper;
        this.versionMapper = versionMapper;
        this.installationMapper = installationMapper;
    }

    @Override
    public Optional<TemplateAggregate> findById(String id) {
        Template t = templateMapper.getById(id);
        return Optional.ofNullable(t).map(TemplateAggregate::reconstitute);
    }

    @Override
    public List<Template> listMarketplace() { return templateMapper.listMarketplace(); }

    @Override
    public List<Template> listMarketplaceByCategory(String category) {
        return templateMapper.listMarketplaceByCategory(category);
    }

    @Override
    public List<Template> listAll() { return templateMapper.listAll(); }

    @Override
    public int countBySlug(String slug) { return templateMapper.countBySlug(slug); }

    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public void save(TemplateAggregate aggregate) {
        Template entity = aggregate.toTemplate();
        if (templateMapper.getById(entity.getId()) == null) {
            templateMapper.insert(entity);
        } else {
            templateMapper.update(entity);
        }
        TemplateVersion pending = aggregate.pendingNewVersion();
        if (pending != null) {
            versionMapper.insert(pending);
            aggregate.clearPendingNewVersion();
        }
    }

    @Override
    public void deleteById(String id) { templateMapper.deleteById(id); }

    @Override
    public TemplateVersion getLatestVersion(String templateId) {
        return versionMapper.getLatestByTemplateId(templateId);
    }

    @Override
    public TemplateVersion getVersion(String templateId, Integer version) {
        return versionMapper.getByTemplateIdAndVersion(templateId, version);
    }

    @Override
    public int countInstallations(String templateId) {
        return installationMapper.countByTemplateId(templateId);
    }
}

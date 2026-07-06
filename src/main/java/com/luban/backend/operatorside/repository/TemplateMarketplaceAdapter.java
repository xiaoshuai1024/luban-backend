package com.luban.backend.operatorside.repository;

import com.luban.backend.shared.entity.Template;
import com.luban.backend.shared.entity.TemplateVersion;
import com.luban.backend.shared.mapper.TemplateInstallationMapper;
import com.luban.backend.shared.mapper.TemplateMapper;
import com.luban.backend.shared.mapper.TemplateVersionMapper;
import com.luban.backend.shared.port.TemplateMarketplacePort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * {@link TemplateMarketplacePort} 实现：聚合三个模板 Mapper 的只读市场查询。
 * publicside 模板市场浏览经此 Port，不直连 Mapper。
 */
@Component
public class TemplateMarketplaceAdapter implements TemplateMarketplacePort {

    private final TemplateMapper templateMapper;
    private final TemplateVersionMapper versionMapper;
    private final TemplateInstallationMapper installationMapper;

    public TemplateMarketplaceAdapter(TemplateMapper templateMapper,
                                      TemplateVersionMapper versionMapper,
                                      TemplateInstallationMapper installationMapper) {
        this.templateMapper = templateMapper;
        this.versionMapper = versionMapper;
        this.installationMapper = installationMapper;
    }

    @Override
    public List<Template> listMarketplace() {
        return templateMapper.listMarketplace();
    }

    @Override
    public List<Template> listMarketplaceByCategory(String category) {
        return templateMapper.listMarketplaceByCategory(category);
    }

    @Override
    public int countInstallations(String templateId) {
        return installationMapper.countByTemplateId(templateId);
    }

    @Override
    public Optional<Template> getById(String id) {
        return Optional.ofNullable(templateMapper.getById(id));
    }

    @Override
    public Optional<TemplateVersion> getLatestVersion(String templateId) {
        return Optional.ofNullable(versionMapper.getLatestByTemplateId(templateId));
    }
}

package com.luban.backend.shared.repository;

import com.luban.backend.shared.domain.TemplateAggregate;
import com.luban.backend.shared.entity.Template;
import com.luban.backend.shared.entity.TemplateVersion;

import java.util.List;
import java.util.Optional;

/**
 * 模板仓储接口（backend-ddd-refactor plan v2 T14）。
 *
 * <p>封装 TemplateMapper + TemplateVersionMapper + TemplateInstallationMapper，
 * TemplateService 零 Mapper 依赖（对齐 FormRepository 封装跨聚合 LeadMapper 的范式）。
 */
public interface TemplateRepository {
    Optional<TemplateAggregate> findById(String id);
    List<Template> listMarketplace();
    List<Template> listMarketplaceByCategory(String category);
    List<Template> listAll();
    int countBySlug(String slug);
    void save(TemplateAggregate aggregate);
    void deleteById(String id);
    TemplateVersion getLatestVersion(String templateId);
    TemplateVersion getVersion(String templateId, Integer version);
    int countInstallations(String templateId);
}

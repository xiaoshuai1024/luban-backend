package com.luban.backend.publicside.service;

import com.luban.backend.shared.domain.TemplateAggregate;
import com.luban.backend.shared.dto.TemplateResponse;
import com.luban.backend.shared.entity.Template;
import com.luban.backend.shared.mapper.TemplateInstallationMapper;
import com.luban.backend.shared.mapper.TemplateMapper;
import com.luban.backend.shared.mapper.TemplateVersionMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * PublicTemplateService — 模板市场公开端 Service（template-marketplace plan）。
 *
 * <p>免鉴权只读，仅返回 published/featured 模板。供 C 端/未登录用户浏览市场。
 */
@Service
public class PublicTemplateService {

    private final TemplateMapper templateMapper;
    private final TemplateVersionMapper versionMapper;
    private final TemplateInstallationMapper installationMapper;

    public PublicTemplateService(TemplateMapper templateMapper,
                                 TemplateVersionMapper versionMapper,
                                 TemplateInstallationMapper installationMapper) {
        this.templateMapper = templateMapper;
        this.versionMapper = versionMapper;
        this.installationMapper = installationMapper;
    }

    /** 市场列表（全部类目，published + featured） */
    public List<TemplateResponse> listMarketplace() {
        return templateMapper.listMarketplace().stream()
                .map(t -> TemplateResponse.fromEntity(t, installationMapper.countByTemplateId(t.getId())))
                .collect(Collectors.toList());
    }

    /** 按类目过滤 */
    public List<TemplateResponse> listByCategory(String category) {
        return templateMapper.listMarketplaceByCategory(category).stream()
                .map(t -> TemplateResponse.fromEntity(t, installationMapper.countByTemplateId(t.getId())))
                .collect(Collectors.toList());
    }

    /** 取模板 schema（仅 published/featured 可见） */
    public String getSchema(String id) {
        Template t = templateMapper.getById(id);
        if (t == null || !TemplateAggregate.isMarketplaceVisible(t.getStatus())) return null;
        var v = versionMapper.getLatestByTemplateId(id);
        return v != null ? v.getSchemaJson() : null;
    }
}

package com.luban.backend.publicside.service;

import com.luban.backend.shared.domain.TemplateAggregate;
import com.luban.backend.shared.dto.TemplateResponse;
import com.luban.backend.shared.entity.Template;
import com.luban.backend.shared.port.TemplateMarketplacePort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * PublicTemplateService — 模板市场公开端 Service（template-marketplace plan）。
 *
 * <p>免鉴权只读，仅返回 published/featured 模板。供 C 端/未登录用户浏览市场。
 * <p>DB 访问经 {@link TemplateMarketplacePort}（依赖倒置），不直连 Mapper。
 */
@Service
public class PublicTemplateService {

    private final TemplateMarketplacePort marketplacePort;

    public PublicTemplateService(TemplateMarketplacePort marketplacePort) {
        this.marketplacePort = marketplacePort;
    }

    /** 市场列表（全部类目，published + featured） */
    public List<TemplateResponse> listMarketplace() {
        return marketplacePort.listMarketplace().stream()
                .map(t -> TemplateResponse.fromEntity(t, marketplacePort.countInstallations(t.getId())))
                .collect(Collectors.toList());
    }

    /** 按类目过滤 */
    public List<TemplateResponse> listByCategory(String category) {
        return marketplacePort.listMarketplaceByCategory(category).stream()
                .map(t -> TemplateResponse.fromEntity(t, marketplacePort.countInstallations(t.getId())))
                .collect(Collectors.toList());
    }

    /** 取模板 schema（仅 published/featured 可见） */
    public String getSchema(String id) {
        Template t = marketplacePort.getById(id).orElse(null);
        if (t == null || !TemplateAggregate.isMarketplaceVisible(t.getStatus())) return null;
        var v = marketplacePort.getLatestVersion(id).orElse(null);
        return v != null ? v.getSchemaJson() : null;
    }
}

package com.luban.backend.shared.port;

import com.luban.backend.shared.entity.Template;
import com.luban.backend.shared.entity.TemplateVersion;

import java.util.List;
import java.util.Optional;

/**
 * 模板市场只读 Port（publicside 依赖倒置）。
 *
 * <p>publicside 模板市场浏览需读 templates / template_versions / template_installations，
 * 但不能直连对应 Mapper。Port 接口置于 shared，实现置于 operatorside。
 * 聚合三类读操作：市场列表、类目过滤、最新版本、安装计数。
 */
public interface TemplateMarketplacePort {

    /** 市场列表（published + featured，featured 优先）。 */
    List<Template> listMarketplace();

    /** 按类目过滤的市场列表。 */
    List<Template> listMarketplaceByCategory(String category);

    /** 模板安装次数（市场展示用）。 */
    int countInstallations(String templateId);

    Optional<Template> getById(String id);

    /** 模板最新版本快照。 */
    Optional<TemplateVersion> getLatestVersion(String templateId);
}

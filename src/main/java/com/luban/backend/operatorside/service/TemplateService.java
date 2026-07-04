package com.luban.backend.operatorside.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luban.backend.shared.auth.UserContext;
import com.luban.backend.shared.domain.TemplateAggregate;
import com.luban.backend.shared.dto.TemplateInstallRequest;
import com.luban.backend.shared.dto.TemplateResponse;
import com.luban.backend.shared.dto.TemplateSaveRequest;
import com.luban.backend.shared.entity.Template;
import com.luban.backend.shared.entity.TemplateVersion;
import com.luban.backend.shared.exception.BusinessException;
import com.luban.backend.shared.mapper.TemplateInstallationMapper;
import com.luban.backend.shared.mapper.TemplateMapper;
import com.luban.backend.shared.mapper.TemplateVersionMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * TemplateService — 模板市场运营端 Service（template-marketplace plan）。
 *
 * <p>职责：模板 CRUD + 状态机（发布/归档/推荐）+ 安装（跨聚合：Template→Page）。
 * 对齐 {@code CampaignService} 范式：注入 Mapper，@Transactional，校验委托聚合根。
 *
 * <p>安装链路：复用 {@link PageService#create} 创建 draft Page + PageVersion 快照，
 * 单事务内完成「拷贝 schema → 创建 page → 记安装审计」。
 */
@Service
public class TemplateService {

    private final TemplateMapper templateMapper;
    private final TemplateVersionMapper versionMapper;
    private final TemplateInstallationMapper installationMapper;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    public TemplateService(TemplateMapper templateMapper,
                           TemplateVersionMapper versionMapper,
                           TemplateInstallationMapper installationMapper,
                           ObjectMapper objectMapper,
                           ApplicationEventPublisher eventPublisher) {
        this.templateMapper = templateMapper;
        this.versionMapper = versionMapper;
        this.installationMapper = installationMapper;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
    }

    // === 查询 ===

    public TemplateResponse get(String id) {
        Template t = templateMapper.getById(id);
        if (t == null) throw BusinessException.templateNotFound();
        return TemplateResponse.fromEntity(t, installationMapper.countByTemplateId(id));
    }

    public List<TemplateResponse> list() {
        return templateMapper.listAll().stream()
                .map(t -> TemplateResponse.fromEntity(t, installationMapper.countByTemplateId(t.getId())))
                .collect(Collectors.toList());
    }

    /** 取模板最新版的 schema（编辑/安装时用） */
    public String getSchema(String id) {
        Template t = templateMapper.getById(id);
        if (t == null) throw BusinessException.templateNotFound();
        TemplateVersion v = versionMapper.getLatestByTemplateId(id);
        if (v == null) throw BusinessException.templateSchemaEmpty();
        return v.getSchemaJson();
    }

    // === CRUD ===

    @Transactional(rollbackFor = Exception.class)
    public TemplateResponse create(TemplateSaveRequest req) {
        if (templateMapper.countBySlug(req.slug()) > 0) throw BusinessException.templateSlugConflict();
        // 工厂内含 slug/category 校验 + schema 非空校验 + 初始版本快照
        TemplateAggregate agg = TemplateAggregate.newTemplate(
                UUID.randomUUID().toString(), req.slug(), req.name(), req.category(),
                req.description(), req.thumbnail(), UserContext.getUserId(),
                req.schemaJson(), req.changeNote());
        templateMapper.insert(agg.toTemplate());
        versionMapper.insert(agg.pendingNewVersion());
        agg.clearPendingNewVersion();
        return TemplateResponse.fromEntity(agg.toTemplate(), 0);
    }

    @Transactional(rollbackFor = Exception.class)
    public TemplateResponse update(String id, TemplateSaveRequest req) {
        Template t = templateMapper.getById(id);
        if (t == null) throw BusinessException.templateNotFound();
        TemplateAggregate.validateSlug(req.slug());
        TemplateAggregate.validateCategory(req.category());

        // slug 改变时校验冲突
        if (!t.getSlug().equals(req.slug()) && templateMapper.countBySlug(req.slug()) > 0) {
            throw BusinessException.templateSlugConflict();
        }

        TemplateAggregate agg = TemplateAggregate.reconstitute(t);
        agg.toTemplate().setSlug(req.slug());
        agg.toTemplate().setName(req.name());
        agg.toTemplate().setCategory(req.category());
        agg.toTemplate().setDescription(req.description());
        agg.toTemplate().setThumbnail(req.thumbnail());
        agg.toTemplate().setUpdatedAt(Instant.now());

        // schema 变更经聚合根 updateSchema（产新版本）
        if (req.schemaJson() != null && !req.schemaJson().isBlank()) {
            agg.updateSchema(req.schemaJson(), req.changeNote());
        }
        templateMapper.update(agg.toTemplate());
        if (agg.pendingNewVersion() != null) {
            versionMapper.insert(agg.pendingNewVersion());
            agg.clearPendingNewVersion();
        }

        return TemplateResponse.fromEntity(agg.toTemplate(), installationMapper.countByTemplateId(id));
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        Template t = templateMapper.getById(id);
        if (t == null) throw BusinessException.templateNotFound();
        templateMapper.deleteById(id); // template_versions FK CASCADE
    }

    // === 状态机（发布/归档/推荐，经聚合根实例方法）===

    @Transactional(rollbackFor = Exception.class)
    public TemplateResponse publish(String id) {
        Template t = templateMapper.getById(id);
        if (t == null) throw BusinessException.templateNotFound();
        TemplateAggregate agg = TemplateAggregate.reconstitute(t);
        agg.publish(versionMapper.getLatestByTemplateId(id) != null);
        templateMapper.update(agg.toTemplate());
        return TemplateResponse.fromEntity(agg.toTemplate(), installationMapper.countByTemplateId(id));
    }

    @Transactional(rollbackFor = Exception.class)
    public TemplateResponse archive(String id) {
        Template t = templateMapper.getById(id);
        if (t == null) throw BusinessException.templateNotFound();
        TemplateAggregate agg = TemplateAggregate.reconstitute(t);
        agg.archive();
        templateMapper.update(agg.toTemplate());
        return TemplateResponse.fromEntity(agg.toTemplate(), installationMapper.countByTemplateId(id));
    }

    @Transactional(rollbackFor = Exception.class)
    public TemplateResponse feature(String id) {
        Template t = templateMapper.getById(id);
        if (t == null) throw BusinessException.templateNotFound();
        TemplateAggregate agg = TemplateAggregate.reconstitute(t);
        agg.feature();
        templateMapper.update(agg.toTemplate());
        return TemplateResponse.fromEntity(agg.toTemplate(), installationMapper.countByTemplateId(id));
    }

    // === 安装（跨聚合解耦：发布 TemplateInstalledEvent，handler 创建 page + installation 审计）===

    /**
     * 安装模板：发布 TemplateInstalledEvent（v2 解耦，替代直接调 PageService.create 的反模式）。
     *
     * <p>聚合根校验 marketplace 可见性 + 发事件。TemplateInstallHandler 消费事件，
     * 在 REQUIRES_NEW 事务创建 draft Page 并写 installation 审计（pageId 同步可得）。
     *
     * <p>pageId 不再同步返回（page 异步创建）：InstallResult 返回 path（确定性，前端导航用）+ version。
     */
    @Transactional(rollbackFor = Exception.class)
    public InstallResult install(String templateId, TemplateInstallRequest req) {
        Template t = templateMapper.getById(templateId);
        if (t == null) throw BusinessException.templateNotFound();

        TemplateVersion v = req.version() != null
                ? versionMapper.getByTemplateIdAndVersion(templateId, req.version())
                : versionMapper.getLatestByTemplateId(templateId);
        if (v == null) throw BusinessException.templateSchemaEmpty();

        JsonNode schemaNode;
        try {
            schemaNode = objectMapper.readTree(v.getSchemaJson());
        } catch (Exception e) {
            throw BusinessException.templateSchemaEmpty();
        }

        String path = req.path() != null && !req.path().isBlank()
                ? req.path()
                : "/templates/" + t.getSlug();

        // 聚合根校验 + 发事件（跨聚合解耦）
        TemplateAggregate agg = TemplateAggregate.reconstitute(t);
        agg.install(schemaNode, req.siteId(), t.getName(), path, v.getVersion());
        templateMapper.update(agg.toTemplate());   // 触发 updatedAt（即使无字段变更，保持原行为）
        agg.pullEvents().forEach(eventPublisher::publishEvent);

        return new InstallResult(path, v.getVersion());
    }

    /** 安装结果。pageId 异步生成（由 handler 创建 page），此处返回 path 供前端导航 + version。 */
    public record InstallResult(String path, int version) {}
}

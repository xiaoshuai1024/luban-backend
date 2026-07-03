package com.luban.backend.operatorside.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luban.backend.operatorside.service.PageService;
import com.luban.backend.shared.domain.TemplateAggregate;
import com.luban.backend.shared.dto.TemplateInstallRequest;
import com.luban.backend.shared.dto.TemplateResponse;
import com.luban.backend.shared.dto.TemplateSaveRequest;
import com.luban.backend.shared.entity.Template;
import com.luban.backend.shared.entity.TemplateInstallation;
import com.luban.backend.shared.entity.TemplateVersion;
import com.luban.backend.shared.exception.BusinessException;
import com.luban.backend.shared.mapper.TemplateInstallationMapper;
import com.luban.backend.shared.mapper.TemplateMapper;
import com.luban.backend.shared.mapper.TemplateVersionMapper;
import com.luban.backend.shared.auth.UserContext;
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
    private final PageService pageService;
    private final ObjectMapper objectMapper;

    public TemplateService(TemplateMapper templateMapper,
                           TemplateVersionMapper versionMapper,
                           TemplateInstallationMapper installationMapper,
                           PageService pageService,
                           ObjectMapper objectMapper) {
        this.templateMapper = templateMapper;
        this.versionMapper = versionMapper;
        this.installationMapper = installationMapper;
        this.pageService = pageService;
        this.objectMapper = objectMapper;
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
        TemplateAggregate.validateSlug(req.slug());
        TemplateAggregate.validateCategory(req.category());
        if (templateMapper.countBySlug(req.slug()) > 0) throw BusinessException.templateSlugConflict();
        if (req.schemaJson() == null || req.schemaJson().isBlank()) throw BusinessException.templateSchemaEmpty();

        Instant now = Instant.now();
        String tplId = UUID.randomUUID().toString();

        Template t = new Template();
        t.setId(tplId);
        t.setSlug(req.slug());
        t.setName(req.name());
        t.setCategory(req.category());
        t.setDescription(req.description());
        t.setThumbnail(req.thumbnail() != null ? req.thumbnail() : "📄");
        t.setAuthorId(UserContext.getUserId()); // 预留 UGC
        t.setStatus(TemplateAggregate.STATUS_DRAFT);
        t.setLatestVersion(1);
        t.setCreatedAt(now);
        t.setUpdatedAt(now);
        templateMapper.insert(t);

        // 初始版本快照
        TemplateVersion v = new TemplateVersion();
        v.setId(UUID.randomUUID().toString());
        v.setTemplateId(tplId);
        v.setVersion(1);
        v.setSchemaJson(req.schemaJson());
        v.setChangeNote(req.changeNote() != null ? req.changeNote() : "初始版本");
        v.setCreatedAt(now);
        versionMapper.insert(v);

        return TemplateResponse.fromEntity(t, 0);
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

        t.setSlug(req.slug());
        t.setName(req.name());
        t.setCategory(req.category());
        t.setDescription(req.description());
        t.setThumbnail(req.thumbnail());
        t.setUpdatedAt(Instant.now());
        templateMapper.update(t);

        // schema 变更则产生新版本
        if (req.schemaJson() != null && !req.schemaJson().isBlank()) {
            int newVersion = t.getLatestVersion() + 1;
            TemplateVersion v = new TemplateVersion();
            v.setId(UUID.randomUUID().toString());
            v.setTemplateId(id);
            v.setVersion(newVersion);
            v.setSchemaJson(req.schemaJson());
            v.setChangeNote(req.changeNote());
            v.setCreatedAt(Instant.now());
            versionMapper.insert(v);
            t.setLatestVersion(newVersion);
            templateMapper.update(t);
        }

        return TemplateResponse.fromEntity(t, installationMapper.countByTemplateId(id));
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        Template t = templateMapper.getById(id);
        if (t == null) throw BusinessException.templateNotFound();
        templateMapper.deleteById(id); // template_versions FK CASCADE
    }

    // === 状态机（发布/归档/推荐）===

    @Transactional(rollbackFor = Exception.class)
    public TemplateResponse publish(String id) {
        Template t = templateMapper.getById(id);
        if (t == null) throw BusinessException.templateNotFound();
        // 发布前校验 schema 存在
        if (versionMapper.getLatestByTemplateId(id) == null) throw BusinessException.templateSchemaEmpty();
        t.setStatus(TemplateAggregate.transitionStatus(t.getStatus(), TemplateAggregate.STATUS_PUBLISHED));
        t.setUpdatedAt(Instant.now());
        templateMapper.update(t);
        return TemplateResponse.fromEntity(t, installationMapper.countByTemplateId(id));
    }

    @Transactional(rollbackFor = Exception.class)
    public TemplateResponse archive(String id) {
        Template t = templateMapper.getById(id);
        if (t == null) throw BusinessException.templateNotFound();
        t.setStatus(TemplateAggregate.transitionStatus(t.getStatus(), TemplateAggregate.STATUS_ARCHIVED));
        t.setUpdatedAt(Instant.now());
        templateMapper.update(t);
        return TemplateResponse.fromEntity(t, installationMapper.countByTemplateId(id));
    }

    @Transactional(rollbackFor = Exception.class)
    public TemplateResponse feature(String id) {
        Template t = templateMapper.getById(id);
        if (t == null) throw BusinessException.templateNotFound();
        t.setStatus(TemplateAggregate.transitionStatus(t.getStatus(), TemplateAggregate.STATUS_FEATURED));
        t.setUpdatedAt(Instant.now());
        templateMapper.update(t);
        return TemplateResponse.fromEntity(t, installationMapper.countByTemplateId(id));
    }

    // === 安装（跨聚合：Template → Page）===

    /**
     * 安装模板：把模板 schema 拷贝为目标 site 下的新 draft Page。
     * 复用 PageService.create（含 path 冲突校验 + 版本快照）。
     */
    @Transactional(rollbackFor = Exception.class)
    public InstallResult install(String templateId, TemplateInstallRequest req) {
        Template t = templateMapper.getById(templateId);
        if (t == null) throw BusinessException.templateNotFound();
        if (!TemplateAggregate.isMarketplaceVisible(t.getStatus())) throw BusinessException.templateNotPublished();

        // 取指定版本或最新版
        TemplateVersion v = req.version() != null
                ? versionMapper.getByTemplateIdAndVersion(templateId, req.version())
                : versionMapper.getLatestByTemplateId(templateId);
        if (v == null) throw BusinessException.templateSchemaEmpty();

        // 解析 schema JSON
        JsonNode schemaNode;
        try {
            schemaNode = objectMapper.readTree(v.getSchemaJson());
        } catch (Exception e) {
            throw BusinessException.templateSchemaEmpty();
        }

        // 目标 path 默认 /templates/{slug}
        String path = req.path() != null && !req.path().isBlank()
                ? req.path()
                : "/templates/" + t.getSlug();

        // 复用 PageService.create 创建 draft Page（含 path 冲突校验 + 版本快照）
        var page = pageService.create(req.siteId(), t.getName(), path, "draft", schemaNode, null);

        // 记安装审计
        TemplateInstallation inst = new TemplateInstallation();
        inst.setId(UUID.randomUUID().toString());
        inst.setTemplateId(templateId);
        inst.setVersion(v.getVersion());
        inst.setSiteId(req.siteId());
        inst.setPageId(page.id());
        inst.setInstallerId(UserContext.getUserId());
        inst.setCreatedAt(Instant.now());
        installationMapper.insert(inst);

        return new InstallResult(page.id(), page.path(), v.getVersion());
    }

    /** 安装结果。 */
    public record InstallResult(String pageId, String path, int version) {}
}

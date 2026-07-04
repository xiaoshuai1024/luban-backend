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
import com.luban.backend.shared.repository.TemplateRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * TemplateService — 模板市场运营端 Service（template-marketplace plan）。
 *
 * <p>职责：模板 CRUD + 状态机（发布/归档/推荐）+ 安装（跨聚合：Template→Page）。
 * 对齐 {@code CampaignService}/{@code SiteService} 范式：注入 {@link TemplateRepository}，
 * @Transactional，校验委托聚合根。TemplateService 零领域 Mapper 依赖（3 个 Mapper 全部封装在 Repository）。
 *
 * <p>安装链路：聚合根发 {@code TemplateInstalledEvent}（v2 跨聚合解耦，替代直接调 PageService.create），
 * 由 {@code TemplateInstallHandler} 消费创建 draft Page + installation 审计。
 */
@Service
public class TemplateService {

    private final TemplateRepository templateRepository;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    public TemplateService(TemplateRepository templateRepository,
                           ObjectMapper objectMapper,
                           ApplicationEventPublisher eventPublisher) {
        this.templateRepository = templateRepository;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
    }

    // === 查询 ===

    public TemplateResponse get(String id) {
        TemplateAggregate agg = templateRepository.findById(id)
                .orElseThrow(BusinessException::templateNotFound);
        return TemplateResponse.fromEntity(agg.toTemplate(), templateRepository.countInstallations(id));
    }

    public List<TemplateResponse> list() {
        return templateRepository.listAll().stream()
                .map(t -> TemplateResponse.fromEntity(t, templateRepository.countInstallations(t.getId())))
                .collect(Collectors.toList());
    }

    /** 取模板最新版的 schema（编辑/安装时用） */
    public String getSchema(String id) {
        templateRepository.findById(id).orElseThrow(BusinessException::templateNotFound);
        TemplateVersion v = templateRepository.getLatestVersion(id);
        if (v == null) throw BusinessException.templateSchemaEmpty();
        return v.getSchemaJson();
    }

    // === CRUD ===

    @Transactional(rollbackFor = Exception.class)
    public TemplateResponse create(TemplateSaveRequest req) {
        if (templateRepository.countBySlug(req.slug()) > 0) throw BusinessException.templateSlugConflict();
        // 工厂内含 slug/category 校验 + schema 非空校验 + 初始版本快照
        TemplateAggregate agg = TemplateAggregate.newTemplate(
                UUID.randomUUID().toString(), req.slug(), req.name(), req.category(),
                req.description(), req.thumbnail(), UserContext.getUserId(),
                req.schemaJson(), req.changeNote());
        // save 处理 insert + 初始版本 v1（pendingNewVersion）
        templateRepository.save(agg);
        return TemplateResponse.fromEntity(agg.toTemplate(), 0);
    }

    @Transactional(rollbackFor = Exception.class)
    public TemplateResponse update(String id, TemplateSaveRequest req) {
        TemplateAggregate agg = templateRepository.findById(id)
                .orElseThrow(BusinessException::templateNotFound);
        Template t = agg.toTemplate();

        // slug 改变时校验冲突
        if (!t.getSlug().equals(req.slug()) && templateRepository.countBySlug(req.slug()) > 0) {
            throw BusinessException.templateSlugConflict();
        }

        // 经聚合根 update（内部校验 slug/category，替代旧 entity mutate + 静态校验器）
        agg.update(req.slug(), req.name(), req.category(), req.description(), req.thumbnail());

        // schema 变更经聚合根 updateSchema（产新版本）
        if (req.schemaJson() != null && !req.schemaJson().isBlank()) {
            agg.updateSchema(req.schemaJson(), req.changeNote());
        }
        // save 处理 update + 新版本（pendingNewVersion）
        templateRepository.save(agg);

        return TemplateResponse.fromEntity(agg.toTemplate(), templateRepository.countInstallations(id));
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        templateRepository.findById(id).orElseThrow(BusinessException::templateNotFound);
        templateRepository.deleteById(id); // template_versions FK CASCADE
    }

    // === 状态机（发布/归档/推荐，经聚合根实例方法）===

    @Transactional(rollbackFor = Exception.class)
    public TemplateResponse publish(String id) {
        TemplateAggregate agg = templateRepository.findById(id)
                .orElseThrow(BusinessException::templateNotFound);
        agg.publish(templateRepository.getLatestVersion(id) != null);
        templateRepository.save(agg);
        return TemplateResponse.fromEntity(agg.toTemplate(), templateRepository.countInstallations(id));
    }

    @Transactional(rollbackFor = Exception.class)
    public TemplateResponse archive(String id) {
        TemplateAggregate agg = templateRepository.findById(id)
                .orElseThrow(BusinessException::templateNotFound);
        agg.archive();
        templateRepository.save(agg);
        return TemplateResponse.fromEntity(agg.toTemplate(), templateRepository.countInstallations(id));
    }

    @Transactional(rollbackFor = Exception.class)
    public TemplateResponse feature(String id) {
        TemplateAggregate agg = templateRepository.findById(id)
                .orElseThrow(BusinessException::templateNotFound);
        agg.feature();
        templateRepository.save(agg);
        return TemplateResponse.fromEntity(agg.toTemplate(), templateRepository.countInstallations(id));
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
        TemplateAggregate agg = templateRepository.findById(templateId)
                .orElseThrow(BusinessException::templateNotFound);
        Template t = agg.toTemplate();

        TemplateVersion v = req.version() != null
                ? templateRepository.getVersion(templateId, req.version())
                : templateRepository.getLatestVersion(templateId);
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

        // 聚合根校验 + 发事件（跨聚合解耦）。schema toString 后传聚合根（领域事件纯 POJO，不携带 JsonNode）。
        agg.install(schemaNode.toString(), req.siteId(), t.getName(), path, v.getVersion());
        templateRepository.save(agg);   // 触发 updatedAt（即使无字段变更，保持原行为）
        agg.pullEvents().forEach(eventPublisher::publishEvent);

        return new InstallResult(path, v.getVersion());
    }

    /** 安装结果。pageId 异步生成（由 handler 创建 page），此处返回 path 供前端导航 + version。 */
    public record InstallResult(String path, int version) {}
}

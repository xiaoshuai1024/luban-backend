package com.luban.backend.shared.domain.event;

import java.time.Instant;

/**
 * 模板安装事件（backend-ddd-refactor plan v2 T4）。
 *
 * <p>由 {@code TemplateAggregate.install()} 发布，{@code TemplateInstallHandler} 消费
 * （替代当前 TemplateService 直接调用 PageService.create 的反模式）。
 *
 * <p>这是 v2 的关键解耦点：Template 聚合不再直接依赖 Page 聚合的 Service，
 * 而是通过事件让 Page 侧的 handler 自行创建 draft Page。
 *
 * <p><b>domain-friendly</b>：schema 以 String 形式携带（G1 修复，避免 Jackson JsonNode
 * 拉进领域层；handler 侧按需 parse）。其它 7 个事件本就纯 JDK。
 *
 * @param templateId 模板 id
 * @param siteId     目标站点 id
 * @param pageName   新页面名称
 * @param path       目标路径
 * @param schemaJson PageSchema JSON 字符串（模板版本快照）
 * @param occurredAt 发生时间
 */
public record TemplateInstalledEvent(
        String templateId,
        String siteId,
        String pageName,
        String path,
        String schemaJson,
        Instant occurredAt
) implements DomainEvent {
    @Override
    public String aggregateId() { return templateId; }
}

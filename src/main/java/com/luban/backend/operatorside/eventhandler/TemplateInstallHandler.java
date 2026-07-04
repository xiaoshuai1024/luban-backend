package com.luban.backend.operatorside.eventhandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.luban.backend.shared.domain.event.TemplateInstalledEvent;
import com.luban.backend.shared.entity.Page;
import com.luban.backend.shared.mapper.PageMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.UUID;

/**
 * 模板安装处理器（backend-ddd-refactor plan v2 T4 / T13）。
 *
 * <p>消费 {@link TemplateInstalledEvent}，创建 draft Page（替代当前 TemplateService 直接调用
 * PageService.create 的跨聚合直接调用反模式）。
 *
 * <p><b>REQUIRES_NEW</b>：在独立事务中创建 Page，避免与模板安装主事务耦合。
 * 非 AFTER_COMMIT：因为安装结果（pageId）需要回填到 TemplateInstallation 记录，
 * 实际由 TemplateService 在同事务内发布事件后由本 handler 在新事务创建 page。
 *
 * <p>注意：当前 PageService.create 含 path 冲突校验 + 版本快照逻辑，
 * 待 T6 PageAggregate 就位后，本 handler 改为通过 PageRepository 操作。
 */
@Component
public class TemplateInstallHandler {

    private static final Logger log = LoggerFactory.getLogger(TemplateInstallHandler.class);

    private final PageMapper pageMapper;

    public TemplateInstallHandler(PageMapper pageMapper) {
        this.pageMapper = pageMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(TemplateInstalledEvent event) {
        JsonNode schema = event.schema();
        Page page = new Page();
        page.setId(UUID.randomUUID().toString());
        page.setSiteId(event.siteId());
        page.setPath(event.path());
        // Page 实体字段为 name（非 title），对齐 pages 表 schema。
        page.setName(event.pageName());
        page.setStatus("draft");
        page.setSchemaJson(schema != null ? schema.toString() : "{}");
        Instant now = Instant.now();
        page.setCreatedAt(now);
        page.setUpdatedAt(now);
        pageMapper.insert(page);
        log.info("Template installed: templateId={}, siteId={}, pageId={}",
                event.templateId(), event.siteId(), page.getId());
    }
}

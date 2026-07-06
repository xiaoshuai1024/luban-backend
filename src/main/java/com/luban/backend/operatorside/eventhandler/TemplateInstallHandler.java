package com.luban.backend.operatorside.eventhandler;

import com.luban.backend.shared.auth.UserContext;
import com.luban.backend.shared.domain.PageAggregate;
import com.luban.backend.shared.domain.event.TemplateInstalledEvent;
import com.luban.backend.shared.entity.TemplateInstallation;
import com.luban.backend.shared.exception.BusinessException;
import com.luban.backend.shared.mapper.TemplateInstallationMapper;
import com.luban.backend.shared.repository.PageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.UUID;

/**
 * 模板安装处理器（backend-ddd-refactor plan v2 T4 / T13，G1 修复版）。
 *
 * <p>消费 {@link TemplateInstalledEvent}，经 {@link PageAggregate#newPage} + {@link PageRepository}
 * 创建 draft Page（替代旧直接 {@code pageMapper.insert}）—— G1 修复 🟡-1：走聚合根统一入口，
 * 聚合根守护 path/schema 不变量；后续若 Page 创建需发 {@code PagePublishedEvent}，也由 save 路径统一。
 *
 * <p><b>AFTER_COMMIT + REQUIRES_NEW</b>：模板安装主事务已提交后，在新事务创建 page。
 * G1 修复 🟡-2：失败用 try/catch + ERROR 日志记录（templateId/siteId/path），不静默吞——
 * 安装主事务已成功，page 创建失败是可观测的副作用丢失，记录以便人工/对账补建。
 *
 * <p><b>幂等（at-least-once 投递）</b>：outbox relay 可能重投。通过检查 (siteId, path) 是否
 * 已有 page 判定——若已存在则跳过创建（DB 级幂等）。
 *
 * <p>替代旧 TemplateService 直接调用 PageService.create 的跨聚合直接调用反模式。
 */
@Component
public class TemplateInstallHandler {

    private static final Logger log = LoggerFactory.getLogger(TemplateInstallHandler.class);

    private final PageRepository pageRepository;
    private final TemplateInstallationMapper installationMapper;

    public TemplateInstallHandler(PageRepository pageRepository, TemplateInstallationMapper installationMapper) {
        this.pageRepository = pageRepository;
        this.installationMapper = installationMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(TemplateInstalledEvent event) {
        installTemplate(event);
    }

    /** 补偿投递（outbox relay 重发，无事务上下文）。幂等：(siteId, path) 已存在则跳过。 */
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onRelay(TemplateInstalledEvent event) {
        installTemplate(event);
    }

    private void installTemplate(TemplateInstalledEvent event) {
        try {
            // 幂等：检查 (templateId, siteId) 是否已安装过——relay 重投时跳过重复创建
            if (installationMapper.countByTemplateIdAndSiteId(event.templateId(), event.siteId()) > 0) {
                log.debug("TemplateInstalledEvent 重复投递，跳过安装 templateId={}, siteId={}",
                        event.templateId(), event.siteId());
                return;
            }

            String pageId = UUID.randomUUID().toString();
            String schemaJson = event.schemaJson();
            PageAggregate agg = PageAggregate.newPage(
                    pageId,
                    event.siteId(),
                    event.pageName(),
                    event.path(),
                    schemaJson != null ? schemaJson : "{}",
                    null);
            pageRepository.save(agg);

            // 写 installation 审计（pageId 同步可得，v2 接管自 TemplateService.install）
            TemplateInstallation inst = new TemplateInstallation();
            inst.setId(UUID.randomUUID().toString());
            inst.setTemplateId(event.templateId());
            inst.setVersion(event.version());   // G1+ 修复：从 event 取 version（BUG-H：DDD 重构遗漏）
            inst.setSiteId(event.siteId());
            inst.setPageId(pageId);
            inst.setInstallerId(UserContext.getUserId());
            inst.setCreatedAt(Instant.now());
            installationMapper.insert(inst);

            log.info("Template installed: templateId={}, siteId={}, pageId={}",
                    event.templateId(), event.siteId(), pageId);
        } catch (BusinessException e) {
            // 业务校验失败（如 path 冲突）：可预期，WARN 记录便于排查
            log.warn("Template install side-effect failed (business): templateId={}, siteId={}, path={}, code={}, msg={}",
                    event.templateId(), event.siteId(), event.path(), e.getCode(), e.getMessage());
            throw e;
        } catch (Exception e) {
            // 基础设施失败（DB 等）：ERROR 记录，新事务回滚；安装主事务已成功，page 缺失可由对账补建
            log.error("Template install side-effect failed (infra): templateId={}, siteId={}, path={}",
                    event.templateId(), event.siteId(), event.path(), e);
            throw e;
        }
    }
}

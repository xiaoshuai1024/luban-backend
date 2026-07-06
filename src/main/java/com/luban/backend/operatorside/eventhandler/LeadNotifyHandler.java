package com.luban.backend.operatorside.eventhandler;

import com.luban.backend.shared.domain.event.LeadSubmittedEvent;
import com.luban.backend.shared.entity.Form;
import com.luban.backend.shared.entity.Lead;
import com.luban.backend.shared.mapper.FormMapper;
import com.luban.backend.shared.mapper.LeadMapper;
import com.luban.backend.shared.repository.FormRepository;
import com.luban.backend.shared.repository.LeadRepository;
import com.luban.backend.shared.support.LeadNotifyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 留资提交事件处理器（backend-ddd-refactor plan v2 T4）。
 *
 * <p>消费 {@link LeadSubmittedEvent}，触发 webhook 通知（替代当前 LeadService 的
 * TransactionSynchronizationManager afterCommit 手动注册）。
 *
 * <p>AFTER_COMMIT + @Async：保证事务提交后异步通知，不阻塞主事务，失败仅日志不回滚留资。
 *
 * <p><b>幂等（at-least-once 投递）</b>：outbox relay 可能重投同一事件。用 leadId 去重——
 * 同一 lead 的通知只发一次（内存 Set，进程级；重启后 outbox 已处理记录不再扫描，安全）。
 * 同时支持 AFTER_COMMIT（即时）与普通 EventListener（relay 补偿）两个入口。
 */
@Component
public class LeadNotifyHandler {

    private static final Logger log = LoggerFactory.getLogger(LeadNotifyHandler.class);

    private final LeadNotifyService leadNotifyService;
    private final LeadRepository leadRepository;
    private final FormRepository formRepository;
    /** 已通知 leadId 集合（幂等去重，防 relay 重投导致重复通知）。 */
    private final Set<String> notifiedLeadIds = ConcurrentHashMap.newKeySet();

    public LeadNotifyHandler(LeadNotifyService leadNotifyService,
                             LeadRepository leadRepository,
                             FormRepository formRepository) {
        this.leadNotifyService = leadNotifyService;
        this.leadRepository = leadRepository;
        this.formRepository = formRepository;
    }

    /** 即时投递（主事务提交后）。 */
    @Async("domainEventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(LeadSubmittedEvent event) {
        handle(event);
    }

    /** 补偿投递（outbox relay 重发，无事务上下文）。幂等：重复事件被去重。 */
    @EventListener
    public void onRelay(LeadSubmittedEvent event) {
        handle(event);
    }

    private void handle(LeadSubmittedEvent event) {
        // 幂等：同一 leadId 只通知一次
        if (!notifiedLeadIds.add(event.leadId())) {
            log.debug("LeadSubmittedEvent 重复投递，跳过通知 leadId={}", event.leadId());
            return;
        }
        // 事件携带 siteId（LeadSubmittedEvent 含 siteId 字段），按 (id, siteId) 精确加载，
        // 对齐多租户查询范式，避免越权读取。经 Repository 聚合根，再取 entity 视图。
        Lead lead = leadRepository.findById(event.leadId(), event.siteId())
                .map(com.luban.backend.shared.domain.LeadAggregate::toLead).orElse(null);
        Form form = formRepository.findFormById(event.formId());
        if (lead != null && form != null) {
            leadNotifyService.notifyNewLead(lead, form);
        }
    }
}

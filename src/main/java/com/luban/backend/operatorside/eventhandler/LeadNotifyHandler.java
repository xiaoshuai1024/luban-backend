package com.luban.backend.operatorside.eventhandler;

import com.luban.backend.shared.domain.event.LeadSubmittedEvent;
import com.luban.backend.shared.entity.Form;
import com.luban.backend.shared.entity.Lead;
import com.luban.backend.shared.mapper.FormMapper;
import com.luban.backend.shared.mapper.LeadMapper;
import com.luban.backend.shared.support.LeadNotifyService;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 留资提交事件处理器（backend-ddd-refactor plan v2 T4）。
 *
 * <p>消费 {@link LeadSubmittedEvent}，触发 webhook 通知（替代当前 LeadService 的
 * TransactionSynchronizationManager afterCommit 手动注册）。
 *
 * <p>AFTER_COMMIT + @Async：保证事务提交后异步通知，不阻塞主事务，失败仅日志不回滚留资。
 */
@Component
public class LeadNotifyHandler {

    private final LeadNotifyService leadNotifyService;
    private final LeadMapper leadMapper;
    private final FormMapper formMapper;

    public LeadNotifyHandler(LeadNotifyService leadNotifyService,
                             LeadMapper leadMapper,
                             FormMapper formMapper) {
        this.leadNotifyService = leadNotifyService;
        this.leadMapper = leadMapper;
        this.formMapper = formMapper;
    }

    @Async("domainEventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(LeadSubmittedEvent event) {
        // 事件携带 siteId（LeadSubmittedEvent 含 siteId 字段），按 (id, siteId) 精确加载，
        // 对齐 LeadMapper 的多租户查询范式（getByIdAndSiteId），避免越权读取。
        Lead lead = leadMapper.getByIdAndSiteId(event.leadId(), event.siteId());
        Form form = formMapper.getById(event.formId());
        if (lead != null && form != null) {
            leadNotifyService.notifyNewLead(lead, form);
        }
    }
}

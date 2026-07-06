package com.luban.backend.operatorside.eventhandler;

import com.luban.backend.shared.domain.LeadAggregate;
import com.luban.backend.shared.domain.event.LeadSubmittedEvent;
import com.luban.backend.shared.entity.Form;
import com.luban.backend.shared.entity.Lead;
import com.luban.backend.shared.repository.FormRepository;
import com.luban.backend.shared.repository.LeadRepository;
import com.luban.backend.shared.support.LeadNotifyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * LeadNotifyHandler 单测（backend-ddd-refactor plan v2 T4 + outbox 幂等）。
 *
 * <p>验证 LeadSubmittedEvent → webhook 通知链路：
 * <ul>
 *   <li>lead + form 都存在 → 触发 notifyNewLead</li>
 *   <li>lead 不存在（越权/已删）→ 不通知（不抛异常，afterCommit 副作用不阻塞）</li>
 *   <li>form 不存在 → 不通知</li>
 *   <li>重复投递同一事件 → 幂等跳过（at-least-once 保障）</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class LeadNotifyHandlerTest {

    @Mock private LeadNotifyService leadNotifyService;
    @Mock private LeadRepository leadRepository;
    @Mock private FormRepository formRepository;

    private LeadNotifyHandler handler;

    @BeforeEach
    void setUp() {
        handler = new LeadNotifyHandler(leadNotifyService, leadRepository, formRepository);
    }

    @Test
    void notifiesWhenLeadAndFormExist() {
        LeadSubmittedEvent event = new LeadSubmittedEvent(
                "lead-1", "form-1", "site-1", Instant.now());
        Lead lead = new Lead();
        lead.setId("lead-1");
        lead.setSiteId("site-1");
        Form form = new Form();
        form.setId("form-1");
        when(leadRepository.findById("lead-1", "site-1")).thenReturn(Optional.of(LeadAggregate.reconstitute(lead)));
        when(formRepository.findFormById("form-1")).thenReturn(form);

        handler.on(event);

        verify(leadNotifyService).notifyNewLead(any(Lead.class), any(Form.class));
    }

    @Test
    void skipsNotifyWhenLeadMissing() {
        LeadSubmittedEvent event = new LeadSubmittedEvent(
                "lead-x", "form-1", "site-1", Instant.now());
        when(leadRepository.findById("lead-x", "site-1")).thenReturn(Optional.empty());

        handler.on(event);

        verify(leadNotifyService, never()).notifyNewLead(any(), any());
    }

    @Test
    void skipsNotifyWhenFormMissing() {
        LeadSubmittedEvent event = new LeadSubmittedEvent(
                "lead-2", "form-x", "site-1", Instant.now());
        Lead lead = new Lead();
        lead.setId("lead-2");
        when(leadRepository.findById("lead-2", "site-1")).thenReturn(Optional.of(LeadAggregate.reconstitute(lead)));
        when(formRepository.findFormById("form-x")).thenReturn(null);

        handler.on(event);

        verify(leadNotifyService, never()).notifyNewLead(any(), any());
    }

    @Test
    void duplicateEventIsIdempotent() {
        // 同一 leadId 的事件投递两次 → 第二次跳过（outbox relay 幂等）
        LeadSubmittedEvent event = new LeadSubmittedEvent(
                "lead-dup", "form-1", "site-1", Instant.now());
        Lead lead = new Lead();
        lead.setId("lead-dup");
        Form form = new Form();
        form.setId("form-1");
        when(leadRepository.findById("lead-dup", "site-1")).thenReturn(Optional.of(LeadAggregate.reconstitute(lead)));
        when(formRepository.findFormById("form-1")).thenReturn(form);

        handler.on(event);
        handler.onRelay(event);   // relay 重投

        // 只通知一次（第二次被幂等去重）
        verify(leadNotifyService).notifyNewLead(any(Lead.class), any(Form.class));
    }
}

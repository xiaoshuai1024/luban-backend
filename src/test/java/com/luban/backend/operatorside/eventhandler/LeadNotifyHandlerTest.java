package com.luban.backend.operatorside.eventhandler;

import com.luban.backend.shared.domain.event.LeadSubmittedEvent;
import com.luban.backend.shared.entity.Form;
import com.luban.backend.shared.entity.Lead;
import com.luban.backend.shared.mapper.FormMapper;
import com.luban.backend.shared.mapper.LeadMapper;
import com.luban.backend.shared.support.LeadNotifyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * LeadNotifyHandler 单测（backend-ddd-refactor plan v2 T4）。
 *
 * <p>验证 LeadSubmittedEvent → webhook 通知链路：
 * <ul>
 *   <li>lead + form 都存在 → 触发 notifyNewLead</li>
 *   <li>lead 不存在（越权/已删）→ 不通知（不抛异常，afterCommit 副作用不阻塞）</li>
 *   <li>form 不存在 → 不通知</li>
 *   <li>使用 (leadId, siteId) 多租户精确查询，防越权读取</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class LeadNotifyHandlerTest {

    @Mock private LeadNotifyService leadNotifyService;
    @Mock private LeadMapper leadMapper;
    @Mock private FormMapper formMapper;

    private LeadNotifyHandler handler;

    @BeforeEach
    void setUp() {
        handler = new LeadNotifyHandler(leadNotifyService, leadMapper, formMapper);
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
        when(leadMapper.getByIdAndSiteId("lead-1", "site-1")).thenReturn(lead);
        when(formMapper.getById("form-1")).thenReturn(form);

        handler.on(event);

        verify(leadNotifyService).notifyNewLead(lead, form);
    }

    @Test
    void skipsNotifyWhenLeadMissing() {
        LeadSubmittedEvent event = new LeadSubmittedEvent(
                "lead-x", "form-1", "site-1", Instant.now());
        when(leadMapper.getByIdAndSiteId("lead-x", "site-1")).thenReturn(null);

        handler.on(event);

        verify(leadNotifyService, never()).notifyNewLead(any(), any());
    }

    @Test
    void skipsNotifyWhenFormMissing() {
        LeadSubmittedEvent event = new LeadSubmittedEvent(
                "lead-1", "form-x", "site-1", Instant.now());
        Lead lead = new Lead();
        lead.setId("lead-1");
        when(leadMapper.getByIdAndSiteId("lead-1", "site-1")).thenReturn(lead);
        when(formMapper.getById("form-x")).thenReturn(null);

        handler.on(event);

        verify(leadNotifyService, never()).notifyNewLead(any(), any());
    }

    @Test
    void queriesLeadByBothIdAndSiteIdForMultiTenantIsolation() {
        // 多租户隔离：必须用 (id, siteId) 双键查询，不能只用 id（防越权读其他站点线索）
        LeadSubmittedEvent event = new LeadSubmittedEvent(
                "lead-1", "form-1", "site-1", Instant.now());
        when(leadMapper.getByIdAndSiteId("lead-1", "site-1")).thenReturn(new Lead());
        when(formMapper.getById("form-1")).thenReturn(new Form());

        handler.on(event);

        // 关键断言：调用的是 getByIdAndSiteId 而非（不存在的）getById
        verify(leadMapper).getByIdAndSiteId("lead-1", "site-1");
    }
}

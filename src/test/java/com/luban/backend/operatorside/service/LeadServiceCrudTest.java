package com.luban.backend.operatorside.service;

import com.luban.backend.shared.crypto.LeadCryptoService;
import com.luban.backend.shared.dto.LeadResponse;
import com.luban.backend.shared.entity.Form;
import com.luban.backend.shared.entity.Lead;
import com.luban.backend.shared.entity.Site;
import com.luban.backend.shared.exception.BusinessException;
import com.luban.backend.shared.mapper.FormMapper;
import com.luban.backend.shared.mapper.LeadAuditLogMapper;
import com.luban.backend.shared.domain.LeadAggregate;
import com.luban.backend.shared.repository.LeadRepository;
import com.luban.backend.shared.mapper.SiteMapper;
import com.luban.backend.shared.mapper.UserSiteMapper;
import com.luban.backend.shared.support.AntiSpamService;
import com.luban.backend.shared.support.DedupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * LeadService 单测补 list/get/exportCsv/transitStatus 正常路径与边界（backend-ddd-refactor T17）。
 * 现有 LeadServiceTest 覆盖 submit + 部分边界（unknown site），本类补齐成功路径与状态机/导出分支。
 */
@ExtendWith(MockitoExtension.class)
class LeadServiceCrudTest {

    @Mock private FormMapper formMapper;
    @Mock private LeadRepository leadRepository;
    @Mock private SiteMapper siteMapper;
    @Mock private LeadAuditLogMapper leadAuditMapper;
    @Mock private UserSiteMapper userSiteMapper;
    @Mock private AntiSpamService antiSpamService;
    @Mock private QuotaService quotaService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private LeadService service;
    private final LeadCryptoService crypto = new LeadCryptoService("");

    private Site sampleSite() {
        Site s = new Site();
        s.setId("site-1");
        s.setName("测试站点");
        return s;
    }

    private Lead sampleLead() {
        Lead l = new Lead();
        l.setId("lead-1");
        l.setSiteId("site-1");
        l.setFormId("form-1");
        l.setStatus("new");
        l.setContactJson(crypto.encrypt("{\"phone\":\"13800000001\",\"name\":\"张三\"}"));
        Instant now = Instant.now();
        l.setCreatedAt(now);
        l.setUpdatedAt(now);
        return l;
    }

    @BeforeEach
    void setUp() {
        TenantGuardService tenantGuard = new TenantGuardService(userSiteMapper);
        service = new LeadService(formMapper, leadRepository, siteMapper, leadAuditMapper, tenantGuard,
                new DedupService(), antiSpamService, crypto, quotaService,
                userSiteMapper, eventPublisher);
        org.mockito.Mockito.lenient().when(userSiteMapper.findOwnerUserId(anyString())).thenReturn(null);
    }

    // ---- list 分页与 keyword 截断 ----

    @Test
    void list_returnsPaginatedWithTotalAndMaskedContact() {
        when(siteMapper.getById("site-1")).thenReturn(sampleSite());
        Lead l = sampleLead();
        when(leadRepository.listByQuery(eq("site-1"), any(), any(), any(), eq(20), eq(10))).thenReturn(List.of(l));
        when(leadRepository.countByQuery(eq("site-1"), any(), any(), any())).thenReturn(1);

        Map<String, Object> result = service.list("site-1", null, null, null, null, 3, 10);

        assertThat(((Number) result.get("total")).intValue()).isEqualTo(1);
        assertThat(((Number) result.get("page")).intValue()).isEqualTo(3);
        assertThat(((Number) result.get("pageSize")).intValue()).isEqualTo(10);
        @SuppressWarnings("unchecked")
        List<LeadResponse> list = (List<LeadResponse>) result.get("list");
        assertThat(list).hasSize(1);
        // phone 脱敏（非明文）
        assertThat(list.get(0).contactMasked().get("phone")).isNotEqualTo("13800000001");
    }

    @Test
    void list_withKeyword_marksTruncatedWhenScanLimitHit() {
        when(siteMapper.getById("site-1")).thenReturn(sampleSite());
        // 触发 truncated：listByQuery 返回正好 scanLimit(500) 条（用单条模拟 size>=500 判定为 true 较难，
        // 改为返回 500 条空 lead 模拟）。这里用 500 个空 lead。
        Lead empty = new Lead();
        empty.setId("e");
        empty.setSiteId("site-1");
        empty.setContactJson(crypto.encrypt("{}"));
        List<Lead> scan500 = java.util.stream.Stream.generate(() -> sampleLead()).limit(500).toList();
        when(leadRepository.listByQuery(eq("site-1"), any(), any(), any(), eq(0), anyInt())).thenReturn(scan500);

        Map<String, Object> result = service.list("site-1", null, null, null, "张三", 1, 10);

        // 全部命中 keyword（contact 含"张三"）→ total=500，truncated=true
        assertThat(result.get("truncated")).isEqualTo(Boolean.TRUE);
        assertThat(((Number) result.get("total")).intValue()).isEqualTo(500);
    }

    @Test
    void list_withKeyword_outOfRangeReturnsEmptyList() {
        when(siteMapper.getById("site-1")).thenReturn(sampleSite());
        when(leadRepository.listByQuery(eq("site-1"), any(), any(), any(), eq(0), anyInt()))
                .thenReturn(List.of(sampleLead()));

        // page=5 size=10 但匹配集只有 1 条 → offset 40 > total 1 → 空列表
        Map<String, Object> result = service.list("site-1", null, null, null, "张三", 5, 10);
        @SuppressWarnings("unchecked")
        List<LeadResponse> list = (List<LeadResponse>) result.get("list");
        assertThat(list).isEmpty();
    }

    // ---- get 正常路径 ----

    @Test
    void get_returnsResponseWithFormName() {
        when(siteMapper.getById("site-1")).thenReturn(sampleSite());
        Lead l = sampleLead();
        when(leadRepository.findById("lead-1", "site-1")).thenReturn(Optional.of(LeadAggregate.reconstitute(l)));
        Form f = new Form();
        f.setId("form-1");
        f.setName("报名表单");
        when(formMapper.getById("form-1")).thenReturn(f);

        LeadResponse resp = service.get("site-1", "lead-1");

        assertThat(resp.id()).isEqualTo("lead-1");
        assertThat(resp.formName()).isEqualTo("报名表单");
    }

    @Test
    void get_throws_leadNotFound_whenLeadMissing() {
        when(siteMapper.getById("site-1")).thenReturn(sampleSite());
        when(leadRepository.findById("lead-x", "site-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get("site-1", "lead-x"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("LEAD_NOT_FOUND");
    }

    // ---- exportCsv ----

    @Test
    void exportCsv_noFilter_usesListAllAndWritesAudit() {
        when(siteMapper.getById("site-1")).thenReturn(sampleSite());
        when(leadRepository.listAllForExport("site-1")).thenReturn(List.of(sampleLead()));

        String csv = service.exportCsv("site-1", null, null, null, "user-9");

        assertThat(csv).contains("phone,email,name,status,created_at");
        assertThat(csv).contains("13800000001"); // 明文（导出场景允许）
        verify(leadRepository).listAllForExport("site-1");
        verify(leadRepository, never()).listForExport(anyString(), any(), any(), any());
        verify(leadAuditMapper).insert(any());
    }

    @Test
    void exportCsv_withFilter_usesListForExport() {
        when(siteMapper.getById("site-1")).thenReturn(sampleSite());
        when(leadRepository.listForExport(eq("site-1"), eq("new"), any(), any())).thenReturn(List.of());

        service.exportCsv("site-1", "new", null, null, "user-9");

        verify(leadRepository).listForExport(eq("site-1"), eq("new"), any(), any());
        verify(leadRepository, never()).listAllForExport(anyString());
    }

    @Test
    void exportCsv_escapesCommaAndQuoteInValues() {
        when(siteMapper.getById("site-1")).thenReturn(sampleSite());
        Lead l = new Lead();
        l.setId("l1");
        l.setSiteId("site-1");
        l.setStatus("new");
        l.setContactJson(crypto.encrypt("{\"phone\":\"138,000\",\"name\":\"a\\\"b\"}"));
        l.setCreatedAt(Instant.now());
        when(leadRepository.listAllForExport("site-1")).thenReturn(List.of(l));

        String csv = service.exportCsv("site-1", null, null, null, "user-9");

        // CSV 转义：含逗号 → 加引号；含引号 → 引号转义为双引号
        assertThat(csv).contains("\"138,000\"");
        assertThat(csv).contains("\"a\"\"b\"");
    }

    // ---- transitStatus ----

    @Test
    void transitStatus_advancesNewToAssigned_setsAssigneeAndWritesAudit() {
        // transitStatus 直接 getOrThrow，不经 ensureSiteExists，无需 stub siteMapper
        Lead l = sampleLead();
        when(leadRepository.findById("lead-1", "site-1")).thenReturn(Optional.of(LeadAggregate.reconstitute(l)));

        LeadResponse resp = service.transitStatus("site-1", "lead-1", "assigned", "user-9");

        assertThat(resp.status()).isEqualTo("assigned");
        assertThat(resp.assigneeId()).isEqualTo("user-9");
        verify(leadRepository).updateStatus(eq("lead-1"), eq("site-1"), eq("assigned"),
                eq("user-9"), any(), any());
        verify(leadAuditMapper).insert(any());
        // new → assigned 不发领域事件（仅 → converted 发 LeadConvertedEvent）
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void transitStatus_converted_setsConvertedAt() {
        // 状态机：new → assigned → contacting → converted（合法路径）
        Lead l = sampleLead();
        l.setStatus("contacting");
        when(leadRepository.findById("lead-1", "site-1")).thenReturn(Optional.of(LeadAggregate.reconstitute(l)));

        LeadResponse resp = service.transitStatus("site-1", "lead-1", "converted", "user-9");

        assertThat(resp.status()).isEqualTo("converted");
        assertThat(resp.convertedAt()).isNotNull();
    }

    @Test
    void transitStatus_invalidTransition_throws() {
        Lead l = sampleLead();
        l.setStatus("converted"); // converted 是终态，无法再转
        when(leadRepository.findById("lead-1", "site-1")).thenReturn(Optional.of(LeadAggregate.reconstitute(l)));

        assertThatThrownBy(() -> service.transitStatus("site-1", "lead-1", "assigned", "user-9"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("LEAD_INVALID_TRANSITION");
        verify(leadRepository, never()).updateStatus(anyString(), anyString(), anyString(), any(), any(), any());
    }

    @Test
    void transitStatus_unknownStatus_throws() {
        when(leadRepository.findById("lead-1", "site-1")).thenReturn(Optional.of(LeadAggregate.reconstitute(sampleLead())));

        assertThatThrownBy(() -> service.transitStatus("site-1", "lead-1", "zzz", "user-9"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("LEAD_INVALID_STATUS");
    }

    // ---- submit: leadDisabled + 配额计数路径（补现有 LeadServiceTest 未覆盖分支） ----

    @Test
    void submit_formDisabled_throwsLeadDisabled() {
        Form f = new Form();
        f.setId("form-1");
        f.setSiteId("site-1");
        f.setStatus("disabled");
        f.setDedupPolicy("reject");
        f.setDedupWindow(86400);
        when(formMapper.getById("form-1")).thenReturn(f);

        assertThatThrownBy(() -> service.submit(new com.luban.backend.shared.dto.LeadSubmitRequest(
                "form-1", Map.of("phone", "13800000001"), null, null, null, "1.2.3.4", "v-1", null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("LEAD_DISABLED");
    }

    @Test
    void submit_ownerFound_invokesQuotaCheck() {
        Form f = new Form();
        f.setId("form-1");
        f.setSiteId("site-1");
        f.setPageId("page-1");
        f.setStatus("active");
        f.setDedupPolicy("reject");
        f.setDedupWindow(86400);
        when(formMapper.getById("form-1")).thenReturn(f);
        when(antiSpamService.isRateLimited(anyString(), anyString(), anyInt(), anyInt())).thenReturn(false);
        when(leadRepository.countByFormHashInWindow(anyString(), anyString(), anyInt())).thenReturn(0);
        when(userSiteMapper.findOwnerUserId("site-1")).thenReturn("owner-1");

        service.submit(new com.luban.backend.shared.dto.LeadSubmitRequest(
                "form-1", Map.of("phone", "13800000001"), "page-1", null, null, "1.2.3.4", "v-1", null));

        verify(quotaService).checkAndIncrement("owner-1", "leads");
    }
}

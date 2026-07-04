package com.luban.backend.operatorside.service;
import com.luban.backend.operatorside.service.LeadService;
import com.luban.backend.shared.crypto.LeadCryptoService;
import com.luban.backend.shared.support.AntiSpamService;
import com.luban.backend.shared.support.DedupService;

import com.luban.backend.shared.dto.LeadSubmitRequest;
import com.luban.backend.shared.dto.LeadSubmitResult;
import com.luban.backend.shared.entity.Form;
import com.luban.backend.shared.entity.Lead;
import com.luban.backend.shared.entity.Site;
import com.luban.backend.shared.exception.BusinessException;
import com.luban.backend.shared.mapper.FormMapper;
import com.luban.backend.shared.mapper.LeadAuditLogMapper;
import com.luban.backend.shared.mapper.LeadMapper;
import com.luban.backend.shared.mapper.SiteMapper;
import com.luban.backend.shared.mapper.UserSiteMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
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
 * LeadService 编排单测：mock mapper/antiSpam/notify，真实 Dedup/Crypto/StatusMachine/TenantGuard。
 * 覆盖提交成功、去重(reject/mark/overwrite/merge)、防刷、captcha、表单不存在、
 * contact 加密、siteId 校验（T-be-2）、keyword 搜索（T-be-3）、解密查看（T-be-5）、tenant authz（🟡）。
 */
@ExtendWith(MockitoExtension.class)
class LeadServiceTest {

    @Mock private FormMapper formMapper;
    @Mock private LeadMapper leadMapper;
    @Mock private SiteMapper siteMapper;
    @Mock private LeadAuditLogMapper leadAuditMapper;
    @Mock private UserSiteMapper userSiteMapper;
    @Mock private AntiSpamService antiSpamService;
    @Mock private QuotaService quotaService;
    @Mock private org.springframework.context.ApplicationEventPublisher eventPublisher;

    private LeadService service;

    private Form sampleForm() {
        Form f = new Form();
        f.setId("form-1");
        f.setSiteId("site-1");
        f.setPageId("page-1");
        f.setName("报名表单");
        f.setDedupWindow(86400);
        f.setDedupPolicy("reject");
        f.setStatus("active");
        return f;
    }

    private Site sampleSite() {
        Site s = new Site();
        s.setId("site-1");
        s.setName("测试站点");
        return s;
    }

    @BeforeEach
    void setup() {
        TenantGuardService tenantGuard = new TenantGuardService(userSiteMapper);
        service = new LeadService(formMapper, leadMapper, siteMapper, leadAuditMapper, tenantGuard,
                new DedupService(), antiSpamService,
                new LeadCryptoService(""), quotaService,
                userSiteMapper, eventPublisher);
        // v02 QuotaService：mock findOwnerUserId 返回 null（跳过配额计数，兼容历史测试）
        org.mockito.Mockito.lenient().when(userSiteMapper.findOwnerUserId(anyString())).thenReturn(null);
    }

    private LeadSubmitRequest req(String phone) {
        return new LeadSubmitRequest("form-1", Map.of("phone", phone, "name", "张三"),
                "page-1", null, null, "1.2.3.4", "visitor-1", null);
    }

    @Test
    void submitSuccessInsertsNewLeadAndNotifies() {
        when(formMapper.getById("form-1")).thenReturn(sampleForm());
        when(antiSpamService.isRateLimited(anyString(), anyString(), anyInt(), anyInt())).thenReturn(false);
        when(leadMapper.countByFormHashInWindow(eq("form-1"), anyString(), anyInt())).thenReturn(0);

        LeadSubmitResult result = service.submit(req("13800000001"));

        assertThat(result.status()).isEqualTo("new");
        assertThat(result.dedup()).isFalse();
        assertThat(result.leadId()).isNotBlank();
        verify(leadMapper).insert(any());
        // notify 通过 afterCommit 回调执行；单元测试无事务上下文时不触发（需集成测试验证）
    }

    @Test
    void submitEncryptsContactNotPlain() {
        when(formMapper.getById("form-1")).thenReturn(sampleForm());
        when(antiSpamService.isRateLimited(anyString(), anyString(), anyInt(), anyInt())).thenReturn(false);
        when(leadMapper.countByFormHashInWindow(anyString(), anyString(), anyInt())).thenReturn(0);

        org.mockito.ArgumentCaptor<com.luban.backend.shared.entity.Lead> captor =
                org.mockito.ArgumentCaptor.forClass(com.luban.backend.shared.entity.Lead.class);

        service.submit(req("13800000001"));
        verify(leadMapper).insert(captor.capture());
        String stored = captor.getValue().getContactJson();
        assertThat(stored).isNotEqualTo("{\"phone\":\"13800000001\"}"); // 非明文
        assertThat(stored).doesNotContain("13800000001"); // 明文不出现在加密串
    }

    @Test
    void submitDuplicateRejectThrows() {
        when(formMapper.getById("form-1")).thenReturn(sampleForm());
        when(antiSpamService.isRateLimited(anyString(), anyString(), anyInt(), anyInt())).thenReturn(false);
        when(leadMapper.countByFormHashInWindow(anyString(), anyString(), anyInt())).thenReturn(1); // 已存在

        assertThatThrownBy(() -> service.submit(req("13800000001")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo("LEAD_DUPLICATE");
        verify(leadMapper, never()).insert(any());
    }

    @Test
    void submitDuplicateMarkInsertsInvalid() {
        Form f = sampleForm();
        f.setDedupPolicy("mark");
        when(formMapper.getById("form-1")).thenReturn(f);
        when(antiSpamService.isRateLimited(anyString(), anyString(), anyInt(), anyInt())).thenReturn(false);
        when(leadMapper.countByFormHashInWindow(anyString(), anyString(), anyInt())).thenReturn(1);

        LeadSubmitResult result = service.submit(req("13800000001"));

        assertThat(result.status()).isEqualTo("invalid");
        assertThat(result.dedup()).isTrue();
        verify(leadMapper).insert(any());
    }

    @Test
    void submitSpamBlockedThrows() {
        when(formMapper.getById("form-1")).thenReturn(sampleForm());
        when(antiSpamService.isRateLimited(anyString(), anyString(), anyInt(), anyInt())).thenReturn(true);

        assertThatThrownBy(() -> service.submit(req("13800000001")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo("LEAD_SPAM_BLOCKED");
        verify(leadMapper, never()).insert(any());
    }

    @Test
    void submitFormNotFoundThrows() {
        when(formMapper.getById("form-1")).thenReturn(null);

        assertThatThrownBy(() -> service.submit(req("13800000001")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo("FORM_NOT_FOUND");
    }

    // ---- T-be-2: siteId 校验 ----

    @Test
    void listRejectsUnknownSiteId() {
        when(siteMapper.getById("ghost")).thenReturn(null);

        assertThatThrownBy(() -> service.list("ghost", null, null, null, null, 1, 20))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo("SITE_NOT_FOUND");
        verify(leadMapper, never()).listByQuery(anyString(), any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void listReturnsResultForValidSiteId() {
        when(siteMapper.getById("site-1")).thenReturn(sampleSite());
        when(leadMapper.listByQuery(eq("site-1"), any(), any(), any(), anyInt(), anyInt())).thenReturn(List.of());
        when(leadMapper.countByQuery(eq("site-1"), any(), any(), any())).thenReturn(0);

        Map<String, Object> result = service.list("site-1", null, null, null, null, 1, 20);

        assertThat(result).containsKeys("list", "total", "page", "pageSize");
        assertThat(result.get("total")).isEqualTo(0);
    }

    @Test
    void listRejectsUnknownSiteIdNoKeyword() {
        when(siteMapper.getById("ghost")).thenReturn(null);
        assertThatThrownBy(() -> service.list("ghost", null, null, null, null, 1, 20))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo("SITE_NOT_FOUND");
    }

    @Test
    void getRejectsUnknownSiteId() {
        when(siteMapper.getById("ghost")).thenReturn(null);

        assertThatThrownBy(() -> service.get("ghost", "lead-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo("SITE_NOT_FOUND");
    }

    @Test
    void exportRejectsUnknownSiteId() {
        when(siteMapper.getById("ghost")).thenReturn(null);

        assertThatThrownBy(() -> service.exportCsv("ghost", null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo("SITE_NOT_FOUND");
        verify(leadMapper, never()).listAllForExport(anyString());
    }

    // ---- T-be-4: OVERWRITE / MERGE / captcha ----

    @Test
    void submitDuplicateOverwriteDeletesOldAndInsertsNew() {
        Form f = sampleForm();
        f.setDedupPolicy("overwrite");
        when(formMapper.getById("form-1")).thenReturn(f);
        when(antiSpamService.isRateLimited(anyString(), anyString(), anyInt(), anyInt())).thenReturn(false);
        when(leadMapper.countByFormHashInWindow(anyString(), anyString(), anyInt())).thenReturn(1);

        LeadSubmitResult result = service.submit(req("13800000001"));

        assertThat(result.dedup()).isTrue();
        // 仅删除窗内旧记录（修复 🔴 跨窗口误删）+ 插入新记录
        verify(leadMapper).deleteByFormHashInWindow(eq("form-1"), anyString(), anyInt());
        verify(leadMapper).insert(any());
    }

    @Test
    void submitDuplicateMergeUpdatesExisting() {
        Form f = sampleForm();
        f.setDedupPolicy("merge");
        when(formMapper.getById("form-1")).thenReturn(f);
        when(antiSpamService.isRateLimited(anyString(), anyString(), anyInt(), anyInt())).thenReturn(false);
        when(leadMapper.countByFormHashInWindow(anyString(), anyString(), anyInt())).thenReturn(1);
        // 旧记录（contact 已加密）
        Lead existing = new Lead();
        existing.setId("lead-old");
        existing.setSiteId("site-1");
        existing.setStatus("new");
        LeadCryptoService crypto = new LeadCryptoService("");
        existing.setContactJson(crypto.encrypt("{\"phone\":\"13800000001\"}"));
        when(leadMapper.getByFormHashInWindow(anyString(), anyString(), anyInt())).thenReturn(existing);

        LeadSubmitResult result = service.submit(req("13800000001"));

        assertThat(result.dedup()).isTrue();
        assertThat(result.leadId()).isEqualTo("lead-old");
        // MERGE：更新旧记录（不插新）
        verify(leadMapper).updateContact(eq("lead-old"), eq("site-1"), anyString(), any(), any());
        verify(leadMapper, never()).insert(any());
    }

    @Test
    void submitCaptchaRequiredAndInvalidThrows() {
        Form f = sampleForm();
        f.setAntiSpamJson("{\"captchaRequired\":true}");
        when(formMapper.getById("form-1")).thenReturn(f);
        when(antiSpamService.verifyCaptcha(null)).thenReturn(false);

        assertThatThrownBy(() -> service.submit(req("13800000001")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo("LEAD_CAPTCHA_INVALID");
        verify(leadMapper, never()).insert(any());
    }

    // ---- T-be-3: keyword 搜索 ----

    @Test
    void listWithKeywordFiltersByDecryptedContact() {
        when(siteMapper.getById("site-1")).thenReturn(sampleSite());
        Lead l = new Lead();
        l.setId("l1");
        l.setSiteId("site-1");
        l.setFormId("form-1");
        l.setStatus("new");
        l.setDedupHash("h");
        LeadCryptoService crypto = new LeadCryptoService("");
        l.setContactJson(crypto.encrypt("{\"phone\":\"13800000001\",\"name\":\"张三\"}"));
        java.time.Instant now = java.time.Instant.now();
        l.setCreatedAt(now);
        l.setUpdatedAt(now);
        Lead l2 = new Lead();
        l2.setId("l2");
        l2.setSiteId("site-1");
        l2.setFormId("form-1");
        l2.setStatus("new");
        l2.setDedupHash("h2");
        l2.setContactJson(crypto.encrypt("{\"phone\":\"13900000000\",\"name\":\"李四\"}"));
        l2.setCreatedAt(now);
        l2.setUpdatedAt(now);
        when(leadMapper.listByQuery(eq("site-1"), any(), any(), any(), eq(0), anyInt()))
                .thenReturn(List.of(l, l2));

        Map<String, Object> result = service.list("site-1", null, null, null, "张三", 1, 20);

        assertThat(((Number) result.get("total")).intValue()).isEqualTo(1);
        @SuppressWarnings("unchecked")
        List<com.luban.backend.shared.dto.LeadResponse> list =
                (List<com.luban.backend.shared.dto.LeadResponse>) result.get("list");
        assertThat(list).hasSize(1);
    }

    // ---- T-be-5: 解密查看 + 审计 ----

    @Test
    void getContactReturnsDecryptedAndWritesAudit() {
        when(siteMapper.getById("site-1")).thenReturn(sampleSite());
        Lead l = new Lead();
        l.setId("l1");
        l.setSiteId("site-1");
        l.setFormId("form-1");
        l.setStatus("new");
        LeadCryptoService crypto = new LeadCryptoService("");
        l.setContactJson(crypto.encrypt("{\"phone\":\"13812345678\",\"name\":\"张三\"}"));
        when(leadMapper.getByIdAndSiteId("l1", "site-1")).thenReturn(l);

        Map<String, String> contact = service.getContact("site-1", "l1", "user-9");

        assertThat(contact.get("phone")).isEqualTo("13812345678");
        assertThat(contact.get("name")).isEqualTo("张三");
        // 审计日志写入
        verify(leadAuditMapper).insert(any());
    }

    @Test
    void getContactRejectsUnknownSiteId() {
        when(siteMapper.getById("ghost")).thenReturn(null);
        assertThatThrownBy(() -> service.getContact("ghost", "l1", "user-9"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo("SITE_NOT_FOUND");
        verify(leadAuditMapper, never()).insert(any());
    }
}

package com.luban.backend.service;

import com.luban.backend.dto.LeadSubmitRequest;
import com.luban.backend.dto.LeadSubmitResult;
import com.luban.backend.entity.Form;
import com.luban.backend.entity.Site;
import com.luban.backend.exception.BusinessException;
import com.luban.backend.mapper.FormMapper;
import com.luban.backend.mapper.LeadMapper;
import com.luban.backend.mapper.SiteMapper;
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
 * LeadService 编排单测：mock mapper/antiSpam/notify，真实 Dedup/Crypto/StatusMachine。
 * 覆盖提交成功、去重(reject/mark)、防刷、表单不存在、contact 加密、siteId 校验（T-be-2）。
 */
@ExtendWith(MockitoExtension.class)
class LeadServiceTest {

    @Mock private FormMapper formMapper;
    @Mock private LeadMapper leadMapper;
    @Mock private SiteMapper siteMapper;
    @Mock private AntiSpamService antiSpamService;
    @Mock private LeadNotifyService notifyService;

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
        service = new LeadService(formMapper, leadMapper, siteMapper, new DedupService(), antiSpamService,
                new LeadCryptoService(""), new LeadStatusMachine(), notifyService);
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
        verify(notifyService).notifyNewLead(any(), any());
    }

    @Test
    void submitEncryptsContactNotPlain() {
        when(formMapper.getById("form-1")).thenReturn(sampleForm());
        when(antiSpamService.isRateLimited(anyString(), anyString(), anyInt(), anyInt())).thenReturn(false);
        when(leadMapper.countByFormHashInWindow(anyString(), anyString(), anyInt())).thenReturn(0);

        org.mockito.ArgumentCaptor<com.luban.backend.entity.Lead> captor =
                org.mockito.ArgumentCaptor.forClass(com.luban.backend.entity.Lead.class);

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

        assertThatThrownBy(() -> service.list("ghost", null, null, null, 1, 20))
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

        Map<String, Object> result = service.list("site-1", null, null, null, 1, 20);

        assertThat(result).containsKeys("list", "total", "page", "pageSize");
        assertThat(result.get("total")).isEqualTo(0);
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

        assertThatThrownBy(() -> service.exportCsv("ghost"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo("SITE_NOT_FOUND");
        verify(leadMapper, never()).listAllForExport(anyString());
    }
}

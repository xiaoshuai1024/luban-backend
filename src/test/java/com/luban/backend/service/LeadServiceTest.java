package com.luban.backend.service;

import com.luban.backend.dto.LeadSubmitRequest;
import com.luban.backend.dto.LeadSubmitResult;
import com.luban.backend.entity.Form;
import com.luban.backend.exception.BusinessException;
import com.luban.backend.mapper.FormMapper;
import com.luban.backend.mapper.LeadMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
 * 覆盖提交成功、去重(reject/mark)、防刷、表单不存在、contact 加密。
 */
@ExtendWith(MockitoExtension.class)
class LeadServiceTest {

    @Mock private FormMapper formMapper;
    @Mock private LeadMapper leadMapper;
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

    @BeforeEach
    void setup() {
        service = new LeadService(formMapper, leadMapper, new DedupService(), antiSpamService,
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
}

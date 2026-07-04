package com.luban.backend.operatorside.service;
import com.luban.backend.operatorside.service.FormService;

import com.luban.backend.shared.domain.FormAggregate;
import com.luban.backend.shared.entity.Form;
import com.luban.backend.shared.exception.BusinessException;
import com.luban.backend.shared.mapper.SiteMapper;
import com.luban.backend.shared.repository.FormRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FormService 删除单测（backend-ddd-refactor plan v2 T8，改造后对齐 Repository 委托）。
 *
 * <p>覆盖正常删除、表单不存在、有线索时拒绝删除（聚合根 assertDeletable 断言决策）。
 * 跨聚合线索计数经 FormRepository.countLeadsByFormId 封装，Service 不直接依赖 LeadMapper。
 */
@ExtendWith(MockitoExtension.class)
class FormServiceTest {

    @Mock private FormRepository formRepository;
    @Mock private SiteMapper siteMapper;

    private FormService service;

    @BeforeEach
    void setUp() {
        service = new FormService(formRepository, siteMapper);
    }

    @Test
    void deleteSucceedsWhenNoLeads() {
        Form form = new Form();
        form.setId("form-1");
        form.setSiteId("site-1");
        when(formRepository.findById("form-1", "site-1")).thenReturn(FormAggregate.reconstitute(form));
        when(formRepository.countLeadsByFormId("form-1")).thenReturn(0);

        service.delete("site-1", "form-1");

        verify(formRepository).countLeadsByFormId("form-1");
        verify(formRepository).deleteById("form-1");
    }

    @Test
    void deleteThrowsWhenFormNotFound() {
        when(formRepository.findById("form-x", "site-1")).thenReturn(null);

        assertThatThrownBy(() -> service.delete("site-1", "form-x"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo("FORM_NOT_FOUND");

        verify(formRepository, never()).deleteById(anyString());
    }

    @Test
    void deleteThrowsWhenHasLeads() {
        Form form = new Form();
        form.setId("form-1");
        form.setSiteId("site-1");
        when(formRepository.findById("form-1", "site-1")).thenReturn(FormAggregate.reconstitute(form));
        when(formRepository.countLeadsByFormId("form-1")).thenReturn(5);

        assertThatThrownBy(() -> service.delete("site-1", "form-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo("FORM_HAS_LEADS");

        verify(formRepository, never()).deleteById(anyString());
    }
}

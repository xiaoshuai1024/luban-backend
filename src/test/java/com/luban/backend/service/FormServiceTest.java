package com.luban.backend.service;

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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FormService 删除单测：覆盖正常删除、表单不存在、有线索时拒绝删除（级联校验）。
 */
@ExtendWith(MockitoExtension.class)
class FormServiceTest {

    @Mock private FormMapper formMapper;
    @Mock private SiteMapper siteMapper;
    @Mock private LeadMapper leadMapper;

    private FormService service;

    @BeforeEach
    void setUp() {
        service = new FormService(formMapper, siteMapper, leadMapper);
    }

    @Test
    void deleteSucceedsWhenNoLeads() {
        Form form = new Form();
        form.setId("form-1");
        form.setSiteId("site-1");
        when(formMapper.getByIdAndSiteId("form-1", "site-1")).thenReturn(form);
        when(leadMapper.countByFormId("form-1")).thenReturn(0);

        service.delete("site-1", "form-1");

        verify(leadMapper).countByFormId("form-1");
        verify(formMapper).deleteById("form-1");
    }

    @Test
    void deleteThrowsWhenFormNotFound() {
        when(formMapper.getByIdAndSiteId("form-x", "site-1")).thenReturn(null);

        assertThatThrownBy(() -> service.delete("site-1", "form-x"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo("FORM_NOT_FOUND");

        verify(formMapper, never()).deleteById(anyString());
    }

    @Test
    void deleteThrowsWhenHasLeads() {
        Form form = new Form();
        form.setId("form-1");
        form.setSiteId("site-1");
        when(formMapper.getByIdAndSiteId("form-1", "site-1")).thenReturn(form);
        when(leadMapper.countByFormId("form-1")).thenReturn(5);

        assertThatThrownBy(() -> service.delete("site-1", "form-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo("FORM_HAS_LEADS");

        verify(formMapper, never()).deleteById(anyString());
    }
}

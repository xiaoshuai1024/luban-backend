package com.luban.backend.operatorside.service;

import com.luban.backend.shared.domain.FormAggregate;
import com.luban.backend.shared.dto.FormResponse;
import com.luban.backend.shared.entity.Form;
import com.luban.backend.shared.entity.Site;
import com.luban.backend.shared.exception.BusinessException;
import com.luban.backend.shared.repository.FormRepository;
import com.luban.backend.shared.repository.SiteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FormService 单测补 create/update/list/get（现有 FormServiceTest 只测 delete）。
 * backend-ddd-refactor plan v2 T18，补覆盖率。
 */
@ExtendWith(MockitoExtension.class)
class FormServiceCrudTest {

    @Mock private FormRepository formRepository;
    @Mock private SiteRepository siteRepository;

    private FormService service;

    @BeforeEach
    void setUp() {
        service = new FormService(formRepository, siteRepository);
    }

    @Test
    void list_returns_forms_when_site_exists() {
        when(siteRepository.existsById("site-1")).thenReturn(true);
        Form f = new Form();
        f.setId("f-1");
        f.setName("表单A");
        when(formRepository.listBySiteId("site-1")).thenReturn(List.of(f));

        List<FormResponse> out = service.list("site-1");

        assertThat(out).hasSize(1);
        assertThat(out.get(0).name()).isEqualTo("表单A");
    }

    @Test
    void list_throws_site_not_found() {
        when(siteRepository.existsById("site-x")).thenReturn(false);

        assertThatThrownBy(() -> service.list("site-x"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("SITE_NOT_FOUND");
    }

    @Test
    void get_returns_form() {
        Form f = new Form();
        f.setId("f-1");
        f.setName("表单");
        f.setDedupPolicy("reject");
        when(formRepository.findById("f-1", "site-1")).thenReturn(FormAggregate.reconstitute(f));

        FormResponse resp = service.get("site-1", "f-1");

        assertThat(resp.name()).isEqualTo("表单");
    }

    @Test
    void get_throws_form_not_found() {
        when(formRepository.findById("f-x", "site-1")).thenReturn(null);

        assertThatThrownBy(() -> service.get("site-1", "f-x"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("FORM_NOT_FOUND");
    }

    @Test
    void create_inserts_when_site_exists() {
        when(siteRepository.existsById("site-1")).thenReturn(true);

        FormResponse resp = service.create(new com.luban.backend.shared.dto.FormSaveRequest(
                "site-1", "page-1", "新表单", null, null, null, null, "reject", null, "active"));

        assertThat(resp.name()).isEqualTo("新表单");
        verify(formRepository).save(any());
    }

    @Test
    void create_throws_when_site_not_found() {
        when(siteRepository.existsById("site-x")).thenReturn(false);

        assertThatThrownBy(() -> service.create(new com.luban.backend.shared.dto.FormSaveRequest(
                "site-x", "p", "n", null, null, null, null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("SITE_NOT_FOUND");
        verify(formRepository, never()).save(any());
    }

    @Test
    void update_modifies_existing() {
        Form f = new Form();
        f.setId("f-1");
        f.setSiteId("site-1");
        f.setName("旧名");
        f.setDedupPolicy("reject");
        when(formRepository.findById("f-1", "site-1")).thenReturn(FormAggregate.reconstitute(f));

        FormResponse resp = service.update("site-1", "f-1",
                new com.luban.backend.shared.dto.FormSaveRequest(
                        "site-1", null, "新名", null, null, null, null, "merge", null, "disabled"));

        assertThat(resp.name()).isEqualTo("新名");
        verify(formRepository).save(any());
    }

    @Test
    void update_throws_when_not_found() {
        when(formRepository.findById("f-x", "site-1")).thenReturn(null);

        assertThatThrownBy(() -> service.update("site-1", "f-x",
                new com.luban.backend.shared.dto.FormSaveRequest("site-1", null, "n", null, null, null, null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("FORM_NOT_FOUND");
    }
}

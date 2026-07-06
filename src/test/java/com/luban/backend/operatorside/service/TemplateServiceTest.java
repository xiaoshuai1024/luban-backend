package com.luban.backend.operatorside.service;

import com.luban.backend.shared.auth.UserContext;
import com.luban.backend.shared.domain.TemplateAggregate;
import com.luban.backend.shared.dto.TemplateInstallRequest;
import com.luban.backend.shared.dto.TemplateResponse;
import com.luban.backend.shared.dto.TemplateSaveRequest;
import com.luban.backend.shared.entity.Template;
import com.luban.backend.shared.entity.TemplateVersion;
import com.luban.backend.shared.exception.BusinessException;
import com.luban.backend.shared.repository.TemplateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.luban.backend.shared.support.DomainEventPublisher;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TemplateService 单测（backend-ddd-refactor plan v2 T18，补覆盖率）。
 *
 * <p>覆盖 create/update/delete/publish/archive/feature/install/get/list/getSchema。
 * v2：TemplateService 注入 {@link TemplateRepository}（零领域 Mapper 依赖），
 * TemplateAggregate 实例方法（newTemplate/reconstitute/publish 等）+ 发事件。
 */
@ExtendWith(MockitoExtension.class)
class TemplateServiceTest {

    @Mock private TemplateRepository templateRepository;
    @Mock private DomainEventPublisher eventPublisher;

    private TemplateService service;

    @BeforeEach
    void setUp() {
        service = new TemplateService(templateRepository, new ObjectMapper(), eventPublisher);
    }

    private TemplateSaveRequest saveReq() {
        return new TemplateSaveRequest("saas-landing", "SaaS 落地页", "saas",
                "desc", null, "{\"components\":[]}", "初始版本");
    }

    private Template publishedTemplate() {
        Template t = new Template();
        t.setId("t-1");
        t.setSlug("saas-landing");
        t.setName("SaaS 落地页");
        t.setCategory("saas");
        t.setStatus("published");
        t.setLatestVersion(1);
        return t;
    }

    private TemplateAggregate aggOf(Template t) {
        return TemplateAggregate.reconstitute(t);
    }

    @Test
    void get_returns_template() {
        Template t = publishedTemplate();
        when(templateRepository.findById("t-1")).thenReturn(Optional.of(aggOf(t)));
        when(templateRepository.countInstallations("t-1")).thenReturn(5);

        TemplateResponse resp = service.get("t-1");

        assertThat(resp.slug()).isEqualTo("saas-landing");
        assertThat(resp.installCount()).isEqualTo(5);
    }

    @Test
    void get_throws_when_not_found() {
        when(templateRepository.findById("t-x")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get("t-x"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("TEMPLATE_NOT_FOUND");
    }

    @Test
    void list_returns_all() {
        Template t = publishedTemplate();
        when(templateRepository.listAll()).thenReturn(List.of(t));
        when(templateRepository.countInstallations("t-1")).thenReturn(0);

        List<TemplateResponse> out = service.list();

        assertThat(out).hasSize(1);
    }

    @Test
    void getSchema_returns_latest_version_schema() {
        when(templateRepository.findById("t-1")).thenReturn(Optional.of(aggOf(publishedTemplate())));
        TemplateVersion v = new TemplateVersion();
        v.setSchemaJson("{\"x\":1}");
        when(templateRepository.getLatestVersion("t-1")).thenReturn(v);

        String schema = service.getSchema("t-1");

        assertThat(schema).isEqualTo("{\"x\":1}");
    }

    @Test
    void getSchema_throws_when_no_version() {
        when(templateRepository.findById("t-1")).thenReturn(Optional.of(aggOf(publishedTemplate())));
        when(templateRepository.getLatestVersion("t-1")).thenReturn(null);

        assertThatThrownBy(() -> service.getSchema("t-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("TEMPLATE_SCHEMA_EMPTY");
    }

    @Test
    void create_inserts_template_and_initial_version() {
        when(templateRepository.countBySlug("saas-landing")).thenReturn(0);

        TemplateResponse resp = service.create(saveReq());

        assertThat(resp.slug()).isEqualTo("saas-landing");
        assertThat(resp.status()).isEqualTo("draft");
        // save 处理 insert + 初始版本 v1（pendingNewVersion）
        verify(templateRepository).save(any());
    }

    @Test
    void create_throws_slug_conflict() {
        when(templateRepository.countBySlug("saas-landing")).thenReturn(1);

        assertThatThrownBy(() -> service.create(saveReq()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("TEMPLATE_SLUG_CONFLICT");
        verify(templateRepository, never()).save(any());
    }

    @Test
    void create_throws_when_schema_blank() {
        when(templateRepository.countBySlug("slug")).thenReturn(0);
        TemplateSaveRequest req = new TemplateSaveRequest("slug", "n", "saas", null, null, "  ", null);

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("TEMPLATE_SCHEMA_EMPTY");
    }

    @Test
    void update_modifies_and_creates_new_version_on_schema_change() {
        when(templateRepository.findById("t-1")).thenReturn(Optional.of(aggOf(publishedTemplate())));
        when(templateRepository.countInstallations("t-1")).thenReturn(0);
        TemplateSaveRequest req = new TemplateSaveRequest("saas-landing", "新名", "saas",
                null, null, "{\"v\":2}", "更新");

        TemplateResponse resp = service.update("t-1", req);

        assertThat(resp.name()).isEqualTo("新名");
        // save 处理 update + 新版本（updateSchema 设置 pendingNewVersion）
        verify(templateRepository).save(any());
    }

    @Test
    void update_throws_when_not_found() {
        when(templateRepository.findById("t-x")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update("t-x", saveReq()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("TEMPLATE_NOT_FOUND");
    }

    @Test
    void publish_sets_published_status() {
        Template t = publishedTemplate();
        t.setStatus("draft");
        when(templateRepository.findById("t-1")).thenReturn(Optional.of(aggOf(t)));
        when(templateRepository.getLatestVersion("t-1")).thenReturn(new TemplateVersion());
        when(templateRepository.countInstallations("t-1")).thenReturn(0);

        TemplateResponse resp = service.publish("t-1");

        assertThat(resp.status()).isEqualTo("published");
        verify(templateRepository).save(any());
    }

    @Test
    void publish_throws_when_no_schema() {
        Template t = publishedTemplate();
        t.setStatus("draft");
        when(templateRepository.findById("t-1")).thenReturn(Optional.of(aggOf(t)));
        when(templateRepository.getLatestVersion("t-1")).thenReturn(null);

        assertThatThrownBy(() -> service.publish("t-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("TEMPLATE_SCHEMA_EMPTY");
    }

    @Test
    void archive_sets_archived_status() {
        when(templateRepository.findById("t-1")).thenReturn(Optional.of(aggOf(publishedTemplate())));
        when(templateRepository.countInstallations("t-1")).thenReturn(0);

        TemplateResponse resp = service.archive("t-1");

        assertThat(resp.status()).isEqualTo("archived");
    }

    @Test
    void feature_sets_featured_status() {
        when(templateRepository.findById("t-1")).thenReturn(Optional.of(aggOf(publishedTemplate())));
        when(templateRepository.countInstallations("t-1")).thenReturn(0);

        TemplateResponse resp = service.feature("t-1");

        assertThat(resp.status()).isEqualTo("featured");
    }

    @Test
    void delete_removes_template() {
        when(templateRepository.findById("t-1")).thenReturn(Optional.of(aggOf(publishedTemplate())));

        service.delete("t-1");

        verify(templateRepository).deleteById("t-1");
    }

    @Test
    void install_publishes_event_for_published_template() {
        Template t = publishedTemplate();
        when(templateRepository.findById("t-1")).thenReturn(Optional.of(aggOf(t)));
        TemplateVersion v = new TemplateVersion();
        v.setSchemaJson("{\"components\":[]}");
        v.setVersion(1);
        when(templateRepository.getLatestVersion("t-1")).thenReturn(v);

        TemplateService.InstallResult result = service.install("t-1",
                new TemplateInstallRequest("site-1", null, null));

        assertThat(result.path()).isEqualTo("/templates/saas-landing");
        assertThat(result.version()).isEqualTo(1);
        verify(eventPublisher).publishAll(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void install_throws_when_not_published() {
        Template t = publishedTemplate();
        t.setStatus("draft");   // draft 不可安装
        when(templateRepository.findById("t-1")).thenReturn(Optional.of(aggOf(t)));
        TemplateVersion v = new TemplateVersion();
        v.setSchemaJson("{}");
        v.setVersion(1);
        when(templateRepository.getLatestVersion("t-1")).thenReturn(v);

        assertThatThrownBy(() -> service.install("t-1",
                new TemplateInstallRequest("site-1", null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("TEMPLATE_NOT_PUBLISHED");
    }

    @Test
    void install_throws_when_schema_empty() {
        when(templateRepository.findById("t-1")).thenReturn(Optional.of(aggOf(publishedTemplate())));
        when(templateRepository.getLatestVersion("t-1")).thenReturn(null);

        assertThatThrownBy(() -> service.install("t-1",
                new TemplateInstallRequest("site-1", null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("TEMPLATE_SCHEMA_EMPTY");
    }
}

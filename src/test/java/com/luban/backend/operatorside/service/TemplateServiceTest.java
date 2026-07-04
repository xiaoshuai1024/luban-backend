package com.luban.backend.operatorside.service;

import com.luban.backend.shared.auth.UserContext;
import com.luban.backend.shared.dto.TemplateInstallRequest;
import com.luban.backend.shared.dto.TemplateResponse;
import com.luban.backend.shared.dto.TemplateSaveRequest;
import com.luban.backend.shared.entity.Template;
import com.luban.backend.shared.entity.TemplateVersion;
import com.luban.backend.shared.exception.BusinessException;
import com.luban.backend.shared.mapper.TemplateInstallationMapper;
import com.luban.backend.shared.mapper.TemplateMapper;
import com.luban.backend.shared.mapper.TemplateVersionMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TemplateService 单测（backend-ddd-refactor plan v2 T18，补覆盖率）。
 *
 * <p>覆盖 create/update/delete/publish/archive/feature/install/get/list/getSchema。
 * TemplateService 现在用 TemplateAggregate 实例方法（newTemplate/reconstitute/publish 等）+ 发事件。
 */
@ExtendWith(MockitoExtension.class)
class TemplateServiceTest {

    @Mock private TemplateMapper templateMapper;
    @Mock private TemplateVersionMapper versionMapper;
    @Mock private TemplateInstallationMapper installationMapper;
    @Mock private ApplicationEventPublisher eventPublisher;

    private TemplateService service;

    @BeforeEach
    void setUp() {
        service = new TemplateService(templateMapper, versionMapper, installationMapper,
                new ObjectMapper(), eventPublisher);
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

    @Test
    void get_returns_template() {
        Template t = publishedTemplate();
        when(templateMapper.getById("t-1")).thenReturn(t);
        when(installationMapper.countByTemplateId("t-1")).thenReturn(5);

        TemplateResponse resp = service.get("t-1");

        assertThat(resp.slug()).isEqualTo("saas-landing");
        assertThat(resp.installCount()).isEqualTo(5);
    }

    @Test
    void get_throws_when_not_found() {
        when(templateMapper.getById("t-x")).thenReturn(null);

        assertThatThrownBy(() -> service.get("t-x"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("TEMPLATE_NOT_FOUND");
    }

    @Test
    void list_returns_all() {
        Template t = publishedTemplate();
        when(templateMapper.listAll()).thenReturn(List.of(t));
        when(installationMapper.countByTemplateId("t-1")).thenReturn(0);

        List<TemplateResponse> out = service.list();

        assertThat(out).hasSize(1);
    }

    @Test
    void getSchema_returns_latest_version_schema() {
        Template t = publishedTemplate();
        when(templateMapper.getById("t-1")).thenReturn(t);
        TemplateVersion v = new TemplateVersion();
        v.setSchemaJson("{\"x\":1}");
        when(versionMapper.getLatestByTemplateId("t-1")).thenReturn(v);

        String schema = service.getSchema("t-1");

        assertThat(schema).isEqualTo("{\"x\":1}");
    }

    @Test
    void getSchema_throws_when_no_version() {
        Template t = publishedTemplate();
        when(templateMapper.getById("t-1")).thenReturn(t);
        when(versionMapper.getLatestByTemplateId("t-1")).thenReturn(null);

        assertThatThrownBy(() -> service.getSchema("t-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("TEMPLATE_SCHEMA_EMPTY");
    }

    @Test
    void create_inserts_template_and_initial_version() {
        when(templateMapper.countBySlug("saas-landing")).thenReturn(0);

        TemplateResponse resp = service.create(saveReq());

        assertThat(resp.slug()).isEqualTo("saas-landing");
        assertThat(resp.status()).isEqualTo("draft");
        verify(templateMapper).insert(any());
        verify(versionMapper).insert(any());   // 初始版本 v1
    }

    @Test
    void create_throws_slug_conflict() {
        when(templateMapper.countBySlug("saas-landing")).thenReturn(1);

        assertThatThrownBy(() -> service.create(saveReq()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("TEMPLATE_SLUG_CONFLICT");
        verify(templateMapper, never()).insert(any());
    }

    @Test
    void create_throws_when_schema_blank() {
        when(templateMapper.countBySlug("slug")).thenReturn(0);
        TemplateSaveRequest req = new TemplateSaveRequest("slug", "n", "saas", null, null, "  ", null);

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("TEMPLATE_SCHEMA_EMPTY");
    }

    @Test
    void update_modifies_and_creates_new_version_on_schema_change() {
        Template existing = publishedTemplate();
        when(templateMapper.getById("t-1")).thenReturn(existing);
        when(installationMapper.countByTemplateId("t-1")).thenReturn(0);
        TemplateSaveRequest req = new TemplateSaveRequest("saas-landing", "新名", "saas",
                null, null, "{\"v\":2}", "更新");

        TemplateResponse resp = service.update("t-1", req);

        assertThat(resp.name()).isEqualTo("新名");
        verify(versionMapper).insert(any());   // 新版本
        verify(templateMapper).update(any());
    }

    @Test
    void update_throws_when_not_found() {
        when(templateMapper.getById("t-x")).thenReturn(null);

        assertThatThrownBy(() -> service.update("t-x", saveReq()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("TEMPLATE_NOT_FOUND");
    }

    @Test
    void publish_sets_published_status() {
        Template t = publishedTemplate();
        t.setStatus("draft");
        when(templateMapper.getById("t-1")).thenReturn(t);
        when(versionMapper.getLatestByTemplateId("t-1")).thenReturn(new TemplateVersion());
        when(installationMapper.countByTemplateId("t-1")).thenReturn(0);

        TemplateResponse resp = service.publish("t-1");

        assertThat(resp.status()).isEqualTo("published");
        verify(templateMapper).update(any());
    }

    @Test
    void publish_throws_when_no_schema() {
        Template t = publishedTemplate();
        t.setStatus("draft");
        when(templateMapper.getById("t-1")).thenReturn(t);
        when(versionMapper.getLatestByTemplateId("t-1")).thenReturn(null);

        assertThatThrownBy(() -> service.publish("t-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("TEMPLATE_SCHEMA_EMPTY");
    }

    @Test
    void archive_sets_archived_status() {
        Template t = publishedTemplate();
        when(templateMapper.getById("t-1")).thenReturn(t);
        when(installationMapper.countByTemplateId("t-1")).thenReturn(0);

        TemplateResponse resp = service.archive("t-1");

        assertThat(resp.status()).isEqualTo("archived");
    }

    @Test
    void feature_sets_featured_status() {
        Template t = publishedTemplate();
        when(templateMapper.getById("t-1")).thenReturn(t);
        when(installationMapper.countByTemplateId("t-1")).thenReturn(0);

        TemplateResponse resp = service.feature("t-1");

        assertThat(resp.status()).isEqualTo("featured");
    }

    @Test
    void delete_removes_template() {
        Template t = publishedTemplate();
        when(templateMapper.getById("t-1")).thenReturn(t);

        service.delete("t-1");

        verify(templateMapper).deleteById("t-1");
    }

    @Test
    void install_publishes_event_for_published_template() {
        Template t = publishedTemplate();
        when(templateMapper.getById("t-1")).thenReturn(t);
        TemplateVersion v = new TemplateVersion();
        v.setSchemaJson("{\"components\":[]}");
        v.setVersion(1);
        when(versionMapper.getLatestByTemplateId("t-1")).thenReturn(v);

        TemplateService.InstallResult result = service.install("t-1",
                new TemplateInstallRequest("site-1", null, null));

        assertThat(result.path()).isEqualTo("/templates/saas-landing");
        assertThat(result.version()).isEqualTo(1);
        verify(eventPublisher).publishEvent(any(com.luban.backend.shared.domain.event.TemplateInstalledEvent.class));
    }

    @Test
    void install_throws_when_not_published() {
        Template t = publishedTemplate();
        t.setStatus("draft");   // draft 不可安装
        when(templateMapper.getById("t-1")).thenReturn(t);
        TemplateVersion v = new TemplateVersion();
        v.setSchemaJson("{}");
        v.setVersion(1);
        when(versionMapper.getLatestByTemplateId("t-1")).thenReturn(v);

        assertThatThrownBy(() -> service.install("t-1",
                new TemplateInstallRequest("site-1", null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("TEMPLATE_NOT_PUBLISHED");
    }

    @Test
    void install_throws_when_schema_empty() {
        Template t = publishedTemplate();
        when(templateMapper.getById("t-1")).thenReturn(t);
        when(versionMapper.getLatestByTemplateId("t-1")).thenReturn(null);

        assertThatThrownBy(() -> service.install("t-1",
                new TemplateInstallRequest("site-1", null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("TEMPLATE_SCHEMA_EMPTY");
    }
}

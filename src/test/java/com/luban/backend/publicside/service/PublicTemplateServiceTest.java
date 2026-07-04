package com.luban.backend.publicside.service;

import com.luban.backend.shared.dto.TemplateResponse;
import com.luban.backend.shared.entity.Template;
import com.luban.backend.shared.entity.TemplateVersion;
import com.luban.backend.shared.mapper.TemplateInstallationMapper;
import com.luban.backend.shared.mapper.TemplateMapper;
import com.luban.backend.shared.mapper.TemplateVersionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * PublicTemplateService 单测（backend-ddd-refactor plan v2 T16，补覆盖率）。
 *
 * <p>覆盖 listMarketplace / listByCategory / getSchema（含 marketplace 可见性判定 + 无版本兜底）。
 * C 端免鉴权只读，仅返回 published/featured 模板。
 */
@ExtendWith(MockitoExtension.class)
class PublicTemplateServiceTest {

    @Mock private TemplateMapper templateMapper;
    @Mock private TemplateVersionMapper versionMapper;
    @Mock private TemplateInstallationMapper installationMapper;

    private PublicTemplateService service;

    @BeforeEach
    void setUp() {
        service = new PublicTemplateService(templateMapper, versionMapper, installationMapper);
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
    void listMarketplace_maps_templates_with_install_counts() {
        Template t = publishedTemplate();
        when(templateMapper.listMarketplace()).thenReturn(List.of(t));
        when(installationMapper.countByTemplateId("t-1")).thenReturn(5);

        List<TemplateResponse> result = service.listMarketplace();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).slug()).isEqualTo("saas-landing");
        assertThat(result.get(0).installCount()).isEqualTo(5);
    }

    @Test
    void listMarketplace_returns_empty_when_no_templates() {
        when(templateMapper.listMarketplace()).thenReturn(List.of());

        List<TemplateResponse> result = service.listMarketplace();

        assertThat(result).isEmpty();
    }

    @Test
    void listByCategory_filters_by_category() {
        Template t = publishedTemplate();
        when(templateMapper.listMarketplaceByCategory("saas")).thenReturn(List.of(t));
        when(installationMapper.countByTemplateId("t-1")).thenReturn(0);

        List<TemplateResponse> result = service.listByCategory("saas");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).category()).isEqualTo("saas");
    }

    @Test
    void getSchema_returns_latest_version_schema_for_published_template() {
        when(templateMapper.getById("t-1")).thenReturn(publishedTemplate());
        TemplateVersion v = new TemplateVersion();
        v.setSchemaJson("{\"components\":[]}");
        when(versionMapper.getLatestByTemplateId("t-1")).thenReturn(v);

        String schema = service.getSchema("t-1");

        assertThat(schema).isEqualTo("{\"components\":[]}");
    }

    @Test
    void getSchema_returns_null_when_template_not_found() {
        when(templateMapper.getById("t-x")).thenReturn(null);

        assertThat(service.getSchema("t-x")).isNull();
    }

    @Test
    void getSchema_returns_null_when_template_not_marketplace_visible() {
        // draft/archived 不可见于市场
        Template t = publishedTemplate();
        t.setStatus("draft");
        when(templateMapper.getById("t-1")).thenReturn(t);

        assertThat(service.getSchema("t-1")).isNull();
    }

    @Test
    void getSchema_returns_null_when_no_version() {
        when(templateMapper.getById("t-1")).thenReturn(publishedTemplate());
        when(versionMapper.getLatestByTemplateId("t-1")).thenReturn(null);

        assertThat(service.getSchema("t-1")).isNull();
    }

    @Test
    void getSchema_returns_schema_for_featured_template() {
        // featured 也属 marketplace 可见
        Template t = publishedTemplate();
        t.setStatus("featured");
        when(templateMapper.getById("t-1")).thenReturn(t);
        TemplateVersion v = new TemplateVersion();
        v.setSchemaJson("{}");
        when(versionMapper.getLatestByTemplateId("t-1")).thenReturn(v);

        assertThat(service.getSchema("t-1")).isEqualTo("{}");
    }
}

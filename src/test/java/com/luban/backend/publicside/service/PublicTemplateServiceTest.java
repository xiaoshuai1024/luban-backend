package com.luban.backend.publicside.service;

import com.luban.backend.shared.dto.TemplateResponse;
import com.luban.backend.shared.entity.Template;
import com.luban.backend.shared.entity.TemplateVersion;
import com.luban.backend.shared.port.TemplateMarketplacePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

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

    @Mock private TemplateMarketplacePort marketplacePort;

    private PublicTemplateService service;

    @BeforeEach
    void setUp() {
        service = new PublicTemplateService(marketplacePort);
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
        when(marketplacePort.listMarketplace()).thenReturn(List.of(t));
        when(marketplacePort.countInstallations("t-1")).thenReturn(5);

        List<TemplateResponse> result = service.listMarketplace();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).slug()).isEqualTo("saas-landing");
        assertThat(result.get(0).installCount()).isEqualTo(5);
    }

    @Test
    void listMarketplace_returns_empty_when_no_templates() {
        when(marketplacePort.listMarketplace()).thenReturn(List.of());

        List<TemplateResponse> result = service.listMarketplace();

        assertThat(result).isEmpty();
    }

    @Test
    void listByCategory_filters_by_category() {
        Template t = publishedTemplate();
        when(marketplacePort.listMarketplaceByCategory("saas")).thenReturn(List.of(t));
        when(marketplacePort.countInstallations("t-1")).thenReturn(0);

        List<TemplateResponse> result = service.listByCategory("saas");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).category()).isEqualTo("saas");
    }

    @Test
    void getSchema_returns_latest_version_schema_for_published_template() {
        when(marketplacePort.getById("t-1")).thenReturn(Optional.of(publishedTemplate()));
        TemplateVersion v = new TemplateVersion();
        v.setSchemaJson("{\"components\":[]}");
        when(marketplacePort.getLatestVersion("t-1")).thenReturn(Optional.of(v));

        String schema = service.getSchema("t-1");

        assertThat(schema).isEqualTo("{\"components\":[]}");
    }

    @Test
    void getSchema_returns_null_when_template_not_found() {
        when(marketplacePort.getById("t-x")).thenReturn(Optional.empty());

        assertThat(service.getSchema("t-x")).isNull();
    }

    @Test
    void getSchema_returns_null_when_template_not_marketplace_visible() {
        // draft/archived 不可见于市场
        Template t = publishedTemplate();
        t.setStatus("draft");
        when(marketplacePort.getById("t-1")).thenReturn(Optional.of(t));

        assertThat(service.getSchema("t-1")).isNull();
    }

    @Test
    void getSchema_returns_null_when_no_version() {
        when(marketplacePort.getById("t-1")).thenReturn(Optional.of(publishedTemplate()));
        when(marketplacePort.getLatestVersion("t-1")).thenReturn(Optional.empty());

        assertThat(service.getSchema("t-1")).isNull();
    }

    @Test
    void getSchema_returns_schema_for_featured_template() {
        // featured 也属 marketplace 可见
        Template t = publishedTemplate();
        t.setStatus("featured");
        when(marketplacePort.getById("t-1")).thenReturn(Optional.of(t));
        TemplateVersion v = new TemplateVersion();
        v.setSchemaJson("{}");
        when(marketplacePort.getLatestVersion("t-1")).thenReturn(Optional.of(v));

        assertThat(service.getSchema("t-1")).isEqualTo("{}");
    }
}
